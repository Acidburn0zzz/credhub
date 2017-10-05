package io.pivotal.security.service;

import io.pivotal.security.auth.UserContext;
import io.pivotal.security.data.PermissionsDataService;
import io.pivotal.security.entity.CredentialName;
import io.pivotal.security.exceptions.EntryNotFoundException;
import io.pivotal.security.exceptions.InvalidAclOperationException;
import io.pivotal.security.request.PermissionEntry;
import io.pivotal.security.request.PermissionOperation;
import org.hamcrest.core.IsEqual;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static io.pivotal.security.request.PermissionOperation.WRITE_ACL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class PermissionServiceTest {
  private static final String CREDENTIAL_NAME = "/test/credential";

  private PermissionService subject;

  private UserContext userContext;
  private PermissionsDataService permissionsDataService;
  private PermissionCheckingService permissionCheckingService;
  private CredentialName expectedCredentialName;

  @Before
  public void beforeEach() {
    userContext = mock(UserContext.class);
    when(userContext.getAclUser()).thenReturn("test-actor");

    permissionsDataService = mock(PermissionsDataService.class);
    permissionCheckingService = mock(PermissionCheckingService.class);

    subject = new PermissionService(permissionsDataService, permissionCheckingService);
  }

  @Test
  public void getAllowedOperations_getsAllowedOperationsUsingPermissionsDataService() {
    ArrayList<PermissionOperation> expectedOperations = newArrayList(PermissionOperation.READ);
    when(permissionsDataService.getAllowedOperations(CREDENTIAL_NAME, "test-actor"))
        .thenReturn(expectedOperations);

    List<PermissionOperation> foundOperations = subject
        .getAllowedOperations(CREDENTIAL_NAME, "test-actor");

    assertThat(expectedOperations, equalTo(foundOperations));
  }

  @Test
  public void saveAccessControlEntries_delegatesToDataService() {
    ArrayList<PermissionEntry> expectedEntries = newArrayList();
    expectedCredentialName = new CredentialName(CREDENTIAL_NAME);
    subject.saveAccessControlEntries(expectedCredentialName, expectedEntries);

    verify(permissionsDataService).saveAccessControlEntries(expectedCredentialName, expectedEntries);
  }

  @Test
  public void getAccessControlList_delegatesToDataService() {
    List<PermissionEntry> expectedPermissionEntries = newArrayList();
    when(permissionsDataService.getAccessControlList(expectedCredentialName))
        .thenReturn(expectedPermissionEntries);
    List<PermissionEntry> foundPermissionEntries = subject.getAccessControlList(expectedCredentialName);

    assertThat(foundPermissionEntries, equalTo(expectedPermissionEntries));
  }

  @Test
  public void deleteAccessControlEntry_whenTheUserHasPermission_delegatesToDataService() {
    when(permissionCheckingService.hasPermission(userContext.getAclUser(), CREDENTIAL_NAME, WRITE_ACL))
        .thenReturn(true);
    when(permissionCheckingService.userAllowedToOperateOnActor(userContext, "other-actor"))
        .thenReturn(true);
    when(permissionsDataService.deleteAccessControlEntry(CREDENTIAL_NAME, "other-actor"))
        .thenReturn(true);
    boolean result = subject.deleteAccessControlEntry(userContext, CREDENTIAL_NAME, "other-actor");

    assertThat(result, equalTo(true));
  }

  @Test
  public void deleteAccessControlEntry_whenTheUserLacksPermission_throwsAnException() {
    when(permissionCheckingService.hasPermission(userContext.getAclUser(), CREDENTIAL_NAME, WRITE_ACL))
        .thenReturn(false);
    when(permissionsDataService.deleteAccessControlEntry(CREDENTIAL_NAME, "other-actor"))
        .thenReturn(true);
    try {
      subject.deleteAccessControlEntry(userContext, CREDENTIAL_NAME, "other-actor");
      fail("should throw");
    } catch( EntryNotFoundException e ){
      assertThat(e.getMessage(), IsEqual.equalTo("error.credential.invalid_access"));
    }
  }

  @Test
  public void deleteAccessControlEntry_whenTheUserIsTheSameAsActor_throwsAnException() {
    when(permissionCheckingService.hasPermission(userContext.getAclUser(), CREDENTIAL_NAME, WRITE_ACL))
        .thenReturn(true);
    when(permissionsDataService.deleteAccessControlEntry(CREDENTIAL_NAME, userContext.getAclUser()))
        .thenReturn(true);
    try {
      subject.deleteAccessControlEntry(userContext, CREDENTIAL_NAME, userContext.getAclUser());
      fail("should throw");
    } catch( InvalidAclOperationException iaoe ){
      assertThat(iaoe.getMessage(), IsEqual.equalTo("error.acl.invalid_update_operation"));
    }
  }
}
