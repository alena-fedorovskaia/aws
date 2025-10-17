package aws.iam;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class IamTest extends IamBaseTest {
  @DataProvider(name = "IamUsersCreation")
  public Object[][] iamUsers() {
    return new Object[][] {
        {"FullAccessUserEC2", "FullAccessGroupEC2"},
        {"FullAccessUserS3", "FullAccessGroupS3"},
        {"ReadAccessUserS3", "ReadAccessGroupS3"}
    };
  }

  @DataProvider(name = "IamGroupsCreation")
  public Object[][] iamGroups() {
    return new Object[][] {
        {"FullAccessGroupEC2", "FullAccessPolicyEC2"},
        {"FullAccessGroupS3", "FullAccessPolicyS3"},
        {"ReadAccessGroupS3", "ReadAccessPolicyS3"}
    };
  }

  @DataProvider(name = "IamRolesCreation")
  public Object[][] iamRoles() {
    return new Object[][] {
        {"FullAccessRoleEC2", "FullAccessPolicyEC2"},
        {"FullAccessRoleS3", "FullAccessPolicyS3"},
        {"ReadAccessRoleS3", "ReadAccessPolicyS3"}
    };
  }

  @DataProvider(name = "IamPoliciesCreation")
  public Object[][] iamPolicies() {
    return new Object[][] {
        {"FullAccessPolicyEC2", "ec2:*", "*", "Allow"},
        {"FullAccessPolicyS3", "s3:*", "*", "Allow"},
        {"ReadAccessPolicyS3", Set.of("s3:Describe*", "s3:Get*", "s3:List*"), "*", "Allow"}
    };
  }

  @Test(description = "Test verifies 3 IAM policies are created according to the given requirements",
      dataProvider = "IamPoliciesCreation")
  public void verifyIAMPoliciesCreation(String name, Object actions, String resources, String effect) throws Exception {
    IamHelper.IamPolicy policy = IamHelper.getPolicy(iam, name);

    Assert.assertEquals(name, policy.name());
    Assert.assertEquals(policy.document().Statement.size(), 1);
    Object a = policy.document().Statement.getFirst().Action instanceof List
        ? new HashSet(((List) policy.document().Statement.getFirst().Action))
        : policy.document().Statement.getFirst().Action;

    Assert.assertEquals(a, actions);
    Assert.assertEquals(policy.document().Statement.getFirst().Resource, resources);
    Assert.assertEquals(policy.document().Statement.getFirst().Effect, effect);
  }

  @Test(description = "Test verifies that 3 roles with specified associated policies were created",
      dataProvider = "IamRolesCreation")
  public void verifyIAMRolesCreation(String role, String policy) {
    Assert.assertTrue(IamHelper.listRolePolicies(iam, role).contains(policy));
  }

  @Test(description = "Test verifies that 3 groups with specified names and policies were created",
      dataProvider = "IamGroupsCreation")
  public void verifyIAMGroupsCreation(String group, String policy) {
    Assert.assertTrue(IamHelper.listGroupPolicies(iam, group).contains(policy));
  }

  @Test(description = "Test verifies that 3 users with specified names were created in specified groups",
      dataProvider = "IamUsersCreation")
  public void verifyIAMUsersCreation(String userName, String userGroup) {
    Assert.assertTrue(IamHelper.listAllUsers(iam).contains(userName));
    Assert.assertTrue(IamHelper.getGroup(iam, userGroup).contains(userName));
  }
}

