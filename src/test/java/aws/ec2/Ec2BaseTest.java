package aws.ec2;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;

public class Ec2BaseTest {
  protected static Ec2Client ec2;

  @BeforeClass
  public void setUp() {
    //AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY environment variables should be set
    ec2 = Ec2Client.builder().credentialsProvider(EnvironmentVariableCredentialsProvider.create())
        .region(Region.EU_CENTRAL_1).build();
  }

  @AfterClass
  public void cleanUp() {
    ec2.close();
  }
}
