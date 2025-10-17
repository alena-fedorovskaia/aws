package aws.iam;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;

public class IamBaseTest {
  protected static IamClient iam;

  @BeforeClass
  public void setUp() {
    //AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY environment variables should be set
    iam = IamClient.builder().credentialsProvider(EnvironmentVariableCredentialsProvider.create())
        .region(Region.EU_CENTRAL_1).build();
  }

  @AfterClass
  public void cleanUp() {
    iam.close();
  }
}
