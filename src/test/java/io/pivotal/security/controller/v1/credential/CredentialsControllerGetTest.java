package io.pivotal.security.controller.v1.credential;

import io.pivotal.security.CredentialManagerApp;
import io.pivotal.security.data.CredentialDataService;
import io.pivotal.security.domain.Encryptor;
import io.pivotal.security.domain.ValueCredential;
import io.pivotal.security.exceptions.KeyNotFoundException;
import io.pivotal.security.helper.AuditingHelper;
import io.pivotal.security.repository.EventAuditRecordRepository;
import io.pivotal.security.repository.RequestAuditRecordRepository;
import io.pivotal.security.service.PermissionCheckingService;
import io.pivotal.security.util.CurrentTimeProvider;
import io.pivotal.security.util.DatabaseProfileResolver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Consumer;

import static com.google.common.collect.Lists.newArrayList;
import static io.pivotal.security.audit.AuditingOperationCode.CREDENTIAL_ACCESS;
import static io.pivotal.security.helper.SpectrumHelper.mockOutCurrentTimeProvider;
import static io.pivotal.security.request.PermissionOperation.READ;
import static io.pivotal.security.util.AuthConstants.UAA_OAUTH2_PASSWORD_GRANT_ACTOR_ID;
import static io.pivotal.security.util.AuthConstants.UAA_OAUTH2_PASSWORD_GRANT_TOKEN;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@ActiveProfiles(value = "unit-test", resolver = DatabaseProfileResolver.class)
@SpringBootTest(classes = CredentialManagerApp.class)
@TestPropertySource(properties = "security.authorization.acls.enabled=false")
@Transactional
public class CredentialsControllerGetTest {
  private static final Instant FROZEN_TIME = Instant.ofEpochSecond(1400011001L);
  private static final String CREDENTIAL_NAME = "/my-namespace/controllerGetTest/credential-name";
  private static final String CREDENTIAL_VALUE = "test value";

  @Autowired
  private WebApplicationContext webApplicationContext;

  @SpyBean
  private Encryptor encryptor;

  @SpyBean
  private PermissionCheckingService permissionCheckingService;

  @Autowired
  private RequestAuditRecordRepository requestAuditRecordRepository;

  @Autowired
  private EventAuditRecordRepository eventAuditRecordRepository;

  @SpyBean
  private CredentialDataService credentialDataService;

  @MockBean
  private CurrentTimeProvider mockCurrentTimeProvider;

  private AuditingHelper auditingHelper;
  private MockMvc mockMvc;

  @Before
  public void beforeEach() {
    Consumer<Long> fakeTimeSetter = mockOutCurrentTimeProvider(mockCurrentTimeProvider);

    fakeTimeSetter.accept(FROZEN_TIME.toEpochMilli());

    mockMvc = MockMvcBuilders
        .webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();

    auditingHelper = new AuditingHelper(requestAuditRecordRepository, eventAuditRecordRepository);
  }

  @Test
  public void gettingACredential_byName_thatExists_returnsTheCredential() throws Exception {
    doReturn(true)
        .when(permissionCheckingService).hasPermission(any(String.class),
        any(String.class), eq(READ));

    UUID uuid = UUID.randomUUID();

    ValueCredential credential = new ValueCredential(CREDENTIAL_NAME)
        .setEncryptor(encryptor)
        .setUuid(uuid)
        .setVersionCreatedAt(FROZEN_TIME);

    doReturn(CREDENTIAL_VALUE).when(encryptor).decrypt(any());

    doReturn(newArrayList(credential)).when(credentialDataService).findAllByName(CREDENTIAL_NAME);

    final MockHttpServletRequestBuilder request = get("/api/v1/data?name=" + CREDENTIAL_NAME)
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON);

    mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$.data[0]" + ".type").value("value"))
        .andExpect(jsonPath("$.data[0]" + ".value").value(CREDENTIAL_VALUE))
        .andExpect(jsonPath("$.data[0]" + ".id").value(uuid.toString()))
        .andExpect(jsonPath("$.data[0]" + ".version_created_at").value(FROZEN_TIME.toString()));

    auditingHelper.verifyAuditing(CREDENTIAL_ACCESS, CREDENTIAL_NAME, UAA_OAUTH2_PASSWORD_GRANT_ACTOR_ID, "/api/v1/data", 200);
  }

  @Test
  public void gettingACredential_byName_whenTheCredentialDoesNotExist_returnsNotFound() throws Exception {
    doReturn(true)
        .when(permissionCheckingService).hasPermission(any(String.class),
        any(String.class), eq(READ));

    final MockHttpServletRequestBuilder get1 = get("/api/v1/data?name=invalid_name")
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON);

    String expectedError1 = "The request could not be completed because the credential does not exist or you do not have sufficient authorization.";
    mockMvc.perform(get1)
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(
            jsonPath("$.error").value(expectedError1)
        );

    auditingHelper.verifyAuditing(
        CREDENTIAL_ACCESS,
        "/invalid_name",
        UAA_OAUTH2_PASSWORD_GRANT_ACTOR_ID,
        "/api/v1/data",
        404
    );
  }

  @Test
  public void gettingACredential_byName_whenTheCredentialDoesNotExist_andCurrentIsSetToTrue_returnsNotFound() throws Exception {
    doReturn(true)
        .when(permissionCheckingService).hasPermission(any(String.class),
        any(String.class), eq(READ));

    final MockHttpServletRequestBuilder get1 = get("/api/v1/data?name=invalid_name&current=true")
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON);

    String expectedError1 = "The request could not be completed because the credential does not exist or you do not have sufficient authorization.";
    mockMvc.perform(get1)
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(
            jsonPath("$.error").value(expectedError1)
        );

    auditingHelper.verifyAuditing(
        CREDENTIAL_ACCESS,
        "/invalid_name",
        UAA_OAUTH2_PASSWORD_GRANT_ACTOR_ID,
        "/api/v1/data",
        404
    );
  }

  @Test
  public void gettingACredential_byName_whenTheUserDoesNotHavePermissionToAccessTheCredential_returnsNotFound() throws Exception {
    doReturn(false)
        .when(permissionCheckingService).hasPermission(any(String.class),
        any(String.class), eq(READ));
    final MockHttpServletRequestBuilder get1 = get("/api/v1/data?name=" + CREDENTIAL_NAME)
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON);

    ResultActions response = mockMvc.perform(get1);

    String expectedError = "The request could not be completed because the credential does not exist or you do not have sufficient authorization.";

    response
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$.error").value(expectedError));

    auditingHelper.verifyAuditing(
        CREDENTIAL_ACCESS,
        CREDENTIAL_NAME,
        UAA_OAUTH2_PASSWORD_GRANT_ACTOR_ID,
        "/api/v1/data",
        404
    );
  }

  @Test
  public void gettingACredential_byName_withCurrentSetToTrue_returnsTheLatestCredential() throws Exception {
    UUID uuid = UUID.randomUUID();
    ValueCredential credential = new ValueCredential(CREDENTIAL_NAME)
        .setEncryptor(encryptor)
        .setUuid(uuid)
        .setVersionCreatedAt(FROZEN_TIME);

    doReturn(CREDENTIAL_VALUE).when(encryptor).decrypt(any());

    doReturn(Arrays.asList(credential)).when(credentialDataService).findNByName(CREDENTIAL_NAME, 1);

    mockMvc.perform(get("/api/v1/data?current=true&name=" + CREDENTIAL_NAME)
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$.data", hasSize(1)));

    verify(credentialDataService).findNByName(CREDENTIAL_NAME, 1);
  }

  @Test
  public void gettingACredential_byName_withCurrentSetToFalse_returnsAllTheCredentialVersions() throws Exception {
    UUID uuid = UUID.randomUUID();
    ValueCredential valueCredential1 = new ValueCredential(CREDENTIAL_NAME)
        .setEncryptor(encryptor)
        .setUuid(uuid)
        .setVersionCreatedAt(FROZEN_TIME);
    ValueCredential valueCredential2 = new ValueCredential(CREDENTIAL_NAME)
        .setEncryptor(encryptor)
        .setUuid(uuid)
        .setVersionCreatedAt(FROZEN_TIME);

    doReturn(CREDENTIAL_VALUE).when(encryptor).decrypt(any());

    doReturn(
        newArrayList(valueCredential1, valueCredential2)
    ).when(credentialDataService).findAllByName(CREDENTIAL_NAME);

    mockMvc.perform(get("/api/v1/data?current=false&name=" + CREDENTIAL_NAME)
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$.data", hasSize(equalTo(2))));
  }

  @Test
  public void gettingACredential_byName_withCurrentSetToFalse_andNumberOfVersions_returnsTheSpecifiedNumberOfVersions() throws Exception {
    UUID uuid = UUID.randomUUID();

    Instant credential1Instant = Instant.ofEpochSecond(1400000001L);
    Instant credential2Instant = Instant.ofEpochSecond(1400000002L);
    Instant credential3Instant = Instant.ofEpochSecond(1400000003L);

    ValueCredential valueCredential1 = new ValueCredential(CREDENTIAL_NAME)
        .setEncryptor(encryptor)
        .setUuid(uuid)
        .setVersionCreatedAt(credential1Instant);
    ValueCredential valueCredential2 = new ValueCredential(CREDENTIAL_NAME)
        .setEncryptor(encryptor)
        .setUuid(uuid)
        .setVersionCreatedAt(credential2Instant);
    ValueCredential valueCredential3 = new ValueCredential(CREDENTIAL_NAME)
        .setEncryptor(encryptor)
        .setUuid(uuid)
        .setVersionCreatedAt(credential3Instant);

    doReturn(CREDENTIAL_VALUE).when(encryptor).decrypt(any());

    doReturn(
        newArrayList(valueCredential1, valueCredential2)
    ).when(credentialDataService).findNByName(CREDENTIAL_NAME, 2);

    mockMvc.perform(get("/api/v1/data?current=false&name=" + CREDENTIAL_NAME + "&versions=2")
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$.data", hasSize(equalTo(2))))
        .andExpect(jsonPath("$.data[0].version_created_at", equalTo(credential1Instant.toString())))
        .andExpect(jsonPath("$.data[1].version_created_at", equalTo(credential2Instant.toString())));
  }

  @Test
  public void gettingACredential_byName_returnsAnErrorWhenTheNameIsNotGiven() throws Exception {
    final MockHttpServletRequestBuilder get = get("/api/v1/data?name=")
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON);

    mockMvc.perform(get)
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(
            jsonPath("$.error")
                .value("The query parameter name is required for this request.")
        );
  }

  @Test
  public void gettingACredential_byId_returnsTheCredentialAndPersistAnAuditEntry() throws Exception {
    UUID uuid = UUID.randomUUID();
    ValueCredential credential = new ValueCredential(CREDENTIAL_NAME)
        .setEncryptor(encryptor)
        .setUuid(uuid)
        .setVersionCreatedAt(FROZEN_TIME);

    doReturn(CREDENTIAL_VALUE).when(encryptor).decrypt(any());
    doReturn(credential).when(credentialDataService).findByUuid(uuid.toString());

    final MockHttpServletRequestBuilder request = get("/api/v1/data/" + uuid)
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON);

    mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$.type").value("value"))
        .andExpect(jsonPath("$.value").value(CREDENTIAL_VALUE))
        .andExpect(jsonPath("$.id").value(uuid.toString()))
        .andExpect(jsonPath("$.version_created_at").value(FROZEN_TIME.toString()));

    auditingHelper.verifyAuditing(
        CREDENTIAL_ACCESS,
        CREDENTIAL_NAME,
        UAA_OAUTH2_PASSWORD_GRANT_ACTOR_ID,
        "/api/v1/data/" + uuid.toString(),
        200);
  }

  @Test
  public void gettingACredential_thatIsEncryptedWithAnUnknownKey_throwsAnException() throws Exception {
    UUID uuid = UUID.randomUUID();
    ValueCredential valueCredential =
        new ValueCredential(CREDENTIAL_NAME)
            .setEncryptor(encryptor)
            .setUuid(uuid)
            .setVersionCreatedAt(FROZEN_TIME);

    doThrow(new KeyNotFoundException("error.missing_encryption_key"))
        .when(encryptor).decrypt(any());
    doReturn(Arrays.asList(valueCredential)).when(credentialDataService)
        .findAllByName(CREDENTIAL_NAME);

    final MockHttpServletRequestBuilder get =
        get("/api/v1/data?name=" + CREDENTIAL_NAME)
            .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
            .accept(APPLICATION_JSON);

    String expectedError = "The credential could not be accessed with the provided encryption keys. You must update your deployment configuration to continue.";

    mockMvc.perform(get)
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$.error").value(expectedError));
  }
}
