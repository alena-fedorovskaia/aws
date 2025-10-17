package aws.iam;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.VisibilityChecker;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.AttachedPolicy;
import software.amazon.awssdk.services.iam.model.GetGroupRequest;
import software.amazon.awssdk.services.iam.model.GetGroupResponse;
import software.amazon.awssdk.services.iam.model.GetPolicyVersionRequest;
import software.amazon.awssdk.services.iam.model.GetPolicyVersionResponse;
import software.amazon.awssdk.services.iam.model.ListAttachedGroupPoliciesRequest;
import software.amazon.awssdk.services.iam.model.ListAttachedGroupPoliciesResponse;
import software.amazon.awssdk.services.iam.model.ListAttachedRolePoliciesRequest;
import software.amazon.awssdk.services.iam.model.ListAttachedRolePoliciesResponse;
import software.amazon.awssdk.services.iam.model.ListPoliciesRequest;
import software.amazon.awssdk.services.iam.model.ListPoliciesResponse;
import software.amazon.awssdk.services.iam.model.ListUsersRequest;
import software.amazon.awssdk.services.iam.model.ListUsersResponse;
import software.amazon.awssdk.services.iam.model.Policy;
import software.amazon.awssdk.services.iam.model.User;

public class IamHelper {
  public static List<String> listAllUsers(IamClient iam) {
    List<String> result = new ArrayList<>();

    boolean done = false;
    String newMarker = null;

    while (!done) {
      ListUsersRequest request;

      if (newMarker == null) {
        request = ListUsersRequest.builder().build();
      } else {
        request = ListUsersRequest.builder().marker(newMarker).build();
      }

      ListUsersResponse response = iam.listUsers(request);
      result.addAll(response.users().stream().map(User::userName).toList());

      if (!response.isTruncated()) {
        done = true;
      } else {
        newMarker = response.marker();
      }
    }

    return result;
  }

  static List<String> getGroup(IamClient iam, String groupName) {
    List<String> result = new ArrayList<>();

    boolean done = false;
    String newMarker = null;

    while (!done) {
      GetGroupRequest request;

      if (newMarker == null) {
        request = GetGroupRequest.builder().groupName(groupName).build();
      } else {
        request = GetGroupRequest.builder().groupName(groupName).marker(newMarker).build();
      }

      GetGroupResponse response = iam.getGroup(request);
      result.addAll(response.users().stream().map(User::userName).toList());

      if (!response.isTruncated()) {
        done = true;
      } else {
        newMarker = response.marker();
      }
    }

    return result;
  }

  static List<String> listGroupPolicies(IamClient iam, String group) {
    List<String> result = new ArrayList<>();

    boolean done = false;
    String newMarker = null;

    while (!done) {
      ListAttachedGroupPoliciesRequest request;

      if (newMarker == null) {
        request = ListAttachedGroupPoliciesRequest.builder().groupName(group).build();
      } else {
        request = ListAttachedGroupPoliciesRequest.builder().groupName(group).marker(newMarker).build();
      }

      ListAttachedGroupPoliciesResponse response = iam.listAttachedGroupPolicies(request);
      result.addAll(response.attachedPolicies().stream().map(AttachedPolicy::policyName).toList());

      if (!response.isTruncated()) {
        done = true;
      } else {
        newMarker = response.marker();
      }
    }

    return result;
  }

  static List<String> listRolePolicies(IamClient iam, String role) {
    List<String> result = new ArrayList<>();

    boolean done = false;
    String newMarker = null;

    while (!done) {
      ListAttachedRolePoliciesRequest request;

      if (newMarker == null) {
        request = ListAttachedRolePoliciesRequest.builder().roleName(role).build();
      } else {
        request = ListAttachedRolePoliciesRequest.builder().roleName(role).marker(newMarker).build();
      }

      ListAttachedRolePoliciesResponse response = iam.listAttachedRolePolicies(request);
      result.addAll(response.attachedPolicies().stream().map(AttachedPolicy::policyName).toList());

      if (!response.isTruncated()) {
        done = true;
      } else {
        newMarker = response.marker();
      }
    }

    return result;
  }

  static IamPolicy getPolicy(IamClient iam, String policyName) throws JsonProcessingException {
    boolean done = false;
    String newMarker = null;

    while (!done) {
      ListPoliciesRequest request;

      if (newMarker == null) {
        request = ListPoliciesRequest.builder().build();
      } else {
        request = ListPoliciesRequest.builder().marker(newMarker).build();
      }

      ListPoliciesResponse response = iam.listPolicies(request);
      Optional<Policy> found = response.policies().stream().filter(policy -> policy.policyName().equals(policyName)).findFirst();

      if (found.isPresent()) {
        //Policy ARN and default version
        String arn = found.get().arn();
        String defaultVersionId = found.get().defaultVersionId();
        String name = found.get().policyName();

        // Get full policy information by ARN and current version
        GetPolicyVersionRequest req =
            GetPolicyVersionRequest.builder().policyArn(arn).versionId(defaultVersionId).build();
        GetPolicyVersionResponse resp = iam.getPolicyVersion(req);

        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.setVisibility(VisibilityChecker.Std.defaultInstance().withFieldVisibility(JsonAutoDetect.Visibility.ANY));

        Document doc = mapper.readValue(java.net.URLDecoder.decode(resp.policyVersion().document()), Document.class);
        return new IamPolicy(name, doc);
      }
      if (!response.isTruncated()) {
        done = true;
      } else {
        newMarker = response.marker();
      }
    }

    // return empty policy
    return new IamPolicy("", null);
  }

  static class Document {
    @JsonProperty
    public List<PolicyDetails> Statement;

    @Override
    public String toString() {
      return  Statement.stream().map(Object::toString).collect(Collectors.joining(", "));
    }
  }

  static class PolicyDetails {
    @JsonProperty
    public Object Action;

    @JsonProperty
    public String Resource;

    @JsonProperty
    public String Effect;

    @Override
    public String toString() {
      return "Action: " + Action + ", Effect: " + Effect + ", Resource: " + Resource;
    }
  }

  record IamPolicy(String name, Document document) {
  }
}