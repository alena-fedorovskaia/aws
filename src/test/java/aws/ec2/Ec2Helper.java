package aws.ec2;

import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeAvailabilityZonesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeAvailabilityZonesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeImagesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeImagesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupRulesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupRulesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeVolumesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeVolumesResponse;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.GroupIdentifier;
import software.amazon.awssdk.services.ec2.model.Image;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.ec2.model.SecurityGroupRule;
import software.amazon.awssdk.services.ec2.model.Volume;

public class Ec2Helper {
  static List<Ec2Instance> getInstances(Ec2Client ec2) {
    List<Ec2Instance> result = new ArrayList<>();

    String nextToken = null;
    do {
      DescribeInstancesRequest request = DescribeInstancesRequest.builder().filters(
          Filter.builder().name("instance-state-name").values("running").build()).nextToken(nextToken).build();
      DescribeInstancesResponse response = ec2.describeInstances(request);

      for (Reservation reservation : response.reservations()) {
        for (Instance instance : reservation.instances()) {
          String rootDeviceId = instance.blockDeviceMappings().getFirst().ebs().volumeId();

          // Get volume details
          DescribeVolumesRequest dvReq = DescribeVolumesRequest.builder().volumeIds(rootDeviceId).build();
          DescribeVolumesResponse dvRes = ec2.describeVolumes(dvReq);
          List<Volume> volumes = dvRes.volumes();

          assert volumes != null;
          assert volumes.size() == 1;

          int instanceVolumeSize = volumes.getFirst().size();
          // Get image details
          String amiId = instance.imageId();
          DescribeImagesRequest diReq = DescribeImagesRequest.builder().imageIds(amiId).build();
          DescribeImagesResponse diRes = ec2.describeImages(diReq);
          List<Image> images = diRes.images();

          assert images != null;
          assert images.size() == 1;
          String imageName = images.getFirst().description();

          // get security rules
          List<String> securityGroupIds = instance.securityGroups().stream().map(GroupIdentifier::groupId).toList();
          DescribeSecurityGroupRulesRequest dsgReq = DescribeSecurityGroupRulesRequest.builder().filters(
              List.of(Filter.builder().name("group-id").values(securityGroupIds).build())).build();
          DescribeSecurityGroupRulesResponse dsgRes = ec2.describeSecurityGroupRules(dsgReq);

          List<SecurityGroupRule> inRules = dsgRes.securityGroupRules().stream().filter(sg -> !sg.isEgress()).toList();
          List<SecurityGroupRule> outRules =
              dsgRes.securityGroupRules().stream().filter(SecurityGroupRule::isEgress).toList();

          String az = instance.placement().availabilityZone();

          DescribeAvailabilityZonesRequest dazReq = DescribeAvailabilityZonesRequest.builder().filters(
              Filter.builder().name("zone-name").values(az).build()).build();
          DescribeAvailabilityZonesResponse dazRes = ec2.describeAvailabilityZones(dazReq);

          assert dazRes.availabilityZones().size() == 1;
          String region = dazRes.availabilityZones().getFirst().regionName();

          Ec2Instance inst = new Ec2Instance(instance.instanceTypeAsString(),
              instance.publicIpAddress() == null,
              instance.tags().stream().map(tag -> tag.key() + ":" + tag.value()).toList(),
              instanceVolumeSize,
              imageName,
              instance.publicIpAddress(),
              instance.privateIpAddress(),
              securityGroupIds,
              inRules,
              outRules,
              az,
              region);

          result.add(inst);
        }
      }
      nextToken = response.nextToken();
    } while (nextToken != null);

    return result;
  }

  record Ec2Instance(
      String type,
      boolean isPrivate,
      List<String> tags,
      int ebsSize,
      String osName,
      String publicIp,
      String privateIp,
      List<String> securityGroupIds,
      List<SecurityGroupRule> inRules,
      List<SecurityGroupRule> outRules,
      String availabilityZone,
      String region
  ) {
  }
}