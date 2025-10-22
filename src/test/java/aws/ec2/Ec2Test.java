package aws.ec2;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;
import software.amazon.awssdk.services.ec2.model.SecurityGroupRule;

public class Ec2Test extends Ec2BaseTest {
  private static final String INSTANCE_TYPE = "t3.micro";
  private static final String INSTANCE_TAGS = "cloudx:qa";
  private static final int INSTANCE_EBS_SIZE = 8;
  private static final String INSTANCE_OS = "Amazon Linux 2";
  private static final String ANY_IP = "0.0.0.0/0";

  @Test(description = "CXQA-EC2-01: 2 application instances should be deployed", groups = "ec2")
  public void verifyTwoInstances() {
    List<Ec2Helper.Ec2Instance> instances = Ec2Helper.getInstances(ec2);

    Assert.assertEquals(instances.size(), 2);
  }

  @Test(description = "CXQA-EC2-02: Each EC2 instance should have the predefined configuration", groups = "ec2")
  public void verifyInstancesConfiguration() {
    //Instance type: t3.micro
    //Instance tags: cloudx: qa
    //Root block device size: 8 GB
    //Instance OS: Amazon Linux 2
    //The public instance should have public IP assigned
    //The private instance should not have public IP assigned
    List<Ec2Helper.Ec2Instance> instances = Ec2Helper.getInstances(ec2);

    Assert.assertTrue(instances.stream().allMatch(ec2Instance -> INSTANCE_TYPE.equals(ec2Instance.type())));
    Assert.assertTrue(instances.stream().allMatch(ec2Instance -> ec2Instance.tags().contains(INSTANCE_TAGS)));
    Assert.assertTrue(instances.stream().allMatch(ec2Instance -> INSTANCE_EBS_SIZE == ec2Instance.ebsSize()));
    Assert.assertTrue(instances.stream().allMatch(ec2Instance -> ec2Instance.osName().contains(INSTANCE_OS)));
    Assert.assertTrue(instances.stream().filter(ec2Instance -> !ec2Instance.isPrivate())
        .allMatch(ec2Instance -> ec2Instance.publicIp() != null));
    Assert.assertTrue(instances.stream().filter(Ec2Helper.Ec2Instance::isPrivate)
        .allMatch(ec2Instance -> ec2Instance.publicIp() == null));
  }

  @Test(description = "CXQA-EC2-03: The security groups' configuration", groups = "ec2")
  public void verifySecurityGroups() {
    //The public instance should be accessible from the internet by SSH (port 22) and HTTP (port 80) only
    //The private instance should be accessible only from the public instance by SSH and HTTP protocols only
    //Both private and public instances should have access to the internet
    List<Ec2Helper.Ec2Instance> instances = Ec2Helper.getInstances(ec2);
    assert instances.size() == 2;

    Ec2Helper.Ec2Instance publicInstance = instances.stream().filter(inst -> !inst.isPrivate()).toList().getFirst();
    assert publicInstance != null;

    // No ports other than 80 and 22 for public
    Assert.assertTrue(
        publicInstance.inRules().stream().map(SecurityGroupRule::toPort).noneMatch(port -> port != 80 && port != 22));
    // ANY IPs are allowed for public
    Assert.assertTrue(publicInstance.inRules().stream().map(SecurityGroupRule::cidrIpv4).allMatch(ANY_IP::equals));

    Ec2Helper.Ec2Instance privateInstance =
        instances.stream().filter(Ec2Helper.Ec2Instance::isPrivate).toList().getFirst();
    assert privateInstance != null;

    String publicSecGroupId = publicInstance.securityGroupIds().getFirst();
    // No ports other than 80 and 22 for private
    Assert.assertTrue(
        privateInstance.inRules().stream().map(SecurityGroupRule::toPort).noneMatch(port -> port != 80 && port != 22));
    // No access from any IP for private
    Assert.assertTrue(privateInstance.inRules().stream().map(SecurityGroupRule::cidrIpv4).noneMatch(ANY_IP::equals));
    // There is reference to public instance security group from private
    Assert.assertTrue(privateInstance.inRules().stream().map(
        rule -> rule.referencedGroupInfo().groupId()).allMatch(publicSecGroupId::equals));

    //ANY IP for outbound rules for both private and public
    Assert.assertTrue(
        instances.stream().flatMap(inst -> inst.outRules().stream()).allMatch(rule -> ANY_IP.equals(rule.cidrIpv4())));
  }

  @Test(description = "CXQA-EC2-04: For Both for public and private instances application API endpoint should " +
      "respond with the correct instance information from EC2 metadata", groups = "ec2")
  public void verifyAPIExposed() throws Exception {
    List<Ec2Helper.Ec2Instance> instances = Ec2Helper.getInstances(ec2);

    Ec2Helper.Ec2Instance publicInstance = instances.stream().filter(inst -> !inst.isPrivate()).toList().getFirst();
    assert publicInstance != null;

    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request =
        HttpRequest.newBuilder().uri(URI.create("http://" + publicInstance.publicIp() + ":80")).build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

    ObjectMapper mapper = new ObjectMapper();
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapper.setVisibility(VisibilityChecker.Std.defaultInstance().withFieldVisibility(JsonAutoDetect.Visibility.ANY));
    ApiResponse apiRes = mapper.readValue(response.body(), ApiResponse.class);

    Assert.assertEquals(apiRes.availability_zone, publicInstance.availabilityZone());
    Assert.assertEquals(apiRes.region, publicInstance.region());
    Assert.assertEquals(apiRes.private_ipv4, publicInstance.privateIp());
  }

  static class ApiResponse {
    @JsonProperty
    public String availability_zone;
    @JsonProperty
    String region;
    @JsonProperty
    String private_ipv4;
  }
}