package org.cloudfoundry.credhub.integration;


import com.jayway.jsonpath.JsonPath;
import org.cloudfoundry.credhub.CredentialManagerApp;
import org.cloudfoundry.credhub.helper.RequestHelper;
import org.cloudfoundry.credhub.util.AuthConstants;
import org.cloudfoundry.credhub.util.DatabaseProfileResolver;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.cloudfoundry.credhub.helper.RequestHelper.*;
import static org.cloudfoundry.credhub.util.AuthConstants.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@ActiveProfiles(value = "unit-test,unit-test-permissions", resolver = DatabaseProfileResolver.class)
@SpringBootTest(classes = CredentialManagerApp.class)
@Transactional
public class CertificateGetTest {

  @Autowired
  private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @Before
  public void beforeEach() throws Exception {
    mockMvc = MockMvcBuilders
        .webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();
  }

  @Test
  public void getCertificateCredentials_returnsAllCertificateCredentials() throws Exception {
    generateCertificateCredential(mockMvc, "/user-a/first-certificate", true, "test", null, USER_A_TOKEN);
    generateCertificateCredential(mockMvc, "/user-a/second-certificate", true, "first-version",
        null, USER_A_TOKEN);
    generateCertificateCredential(mockMvc, "/user-a/second-certificate", true, "second-version",
        null, USER_A_TOKEN);
    generatePassword(mockMvc, "/user-a/invalid-cert", true, null, USER_A_TOKEN);
    String response = getCertificateCredentials(mockMvc, USER_A_TOKEN);

    List<String> names = JsonPath.parse(response).read("$.certificates[*].name");

    assertThat(names.size(), greaterThanOrEqualTo(2));
    assertThat(names, hasItems("/user-a/first-certificate", "/user-a/second-certificate"));
    assertThat(names, not(hasItems("/user-a/invalid-cert")));
  }

  @Test
  public void getCertificateCredentials_returnsOnlyCertificatesTheUserCanAccess() throws Exception {
    generateCa(mockMvc, "/user-a/certificate", USER_A_TOKEN);
    generateCa(mockMvc, "/user-b/certificate", USER_B_TOKEN);
    generateCa(mockMvc, "/shared-read-only/certificate", ALL_PERMISSIONS_TOKEN);

    String response = getCertificateCredentials(mockMvc, USER_A_TOKEN);
    List<String> names = JsonPath.parse(response)
        .read("$.certificates[*].name");

    assertThat(names.size(), greaterThanOrEqualTo(2));
    assertThat(names, hasItems("/user-a/certificate", "/shared-read-only/certificate"));
    assertThat(names, not(hasItems("/user-b/certificate")));

    response = getCertificateCredentials(mockMvc, USER_B_TOKEN);
    names = JsonPath.parse(response)
        .read("$.certificates[*].name");

    assertThat(names.size(), greaterThanOrEqualTo(2));
    assertThat(names, hasItems("/user-b/certificate", "/shared-read-only/certificate"));
    assertThat(names, not(hasItems("/user-a/certificate")));
  }

  @Test
  public void getCertificateCredentials_withNameProvided_returnsACertificateWithThatName() throws Exception {
    generateCa(mockMvc, "my-certificate", ALL_PERMISSIONS_TOKEN);
    generateCa(mockMvc, "also-my-certificate", ALL_PERMISSIONS_TOKEN);

    String response = getCertificateCredentialsByName(mockMvc, ALL_PERMISSIONS_TOKEN, "my-certificate");
    List<String> names = JsonPath.parse(response)
        .read("$.certificates[*].name");

    assertThat(names, hasSize(1));
    assertThat(names, containsInAnyOrder("/my-certificate"));
  }

  @Test
  public void getCertificateCredentials_whenNameDoesNotMatchACredential_returns404WithMessage() throws Exception {
    MockHttpServletRequestBuilder get = get("/api/v1/certificates?name=" + "some-other-certificate")
        .header("Authorization", "Bearer " + ALL_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON);

    String response = mockMvc.perform(get)
        .andDo(print())
        .andExpect(status().isNotFound())
        .andReturn().getResponse().getContentAsString();

    assertThat(response, containsString(
        "The request could not be completed because the credential does not exist or you do not have sufficient authorization."));
  }

  @Test
  public void getCertificateCredentialsByName_doesNotReturnOtherCredentialTypes() throws Exception {
    generatePassword(mockMvc, "my-credential", true, 10, ALL_PERMISSIONS_TOKEN);

    MockHttpServletRequestBuilder get = get("/api/v1/certificates?name=" + "my-credential")
        .header("Authorization", "Bearer " + ALL_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON);

    String response = mockMvc.perform(get)
        .andDo(print())
        .andExpect(status().isNotFound())
        .andReturn().getResponse().getContentAsString();

    assertThat(response, containsString(
        "The request could not be completed because the credential does not exist or you do not have sufficient authorization."));
  }

  @Test
  public void getCertificateCredentials_whenNameIsProvided_andUserDoesNotHaveRequiredPermissions_returns404WithMessage()
      throws Exception {
    generateCa(mockMvc, "my-certificate", ALL_PERMISSIONS_TOKEN);

    MockHttpServletRequestBuilder get = get("/api/v1/certificates?name=" + "my-certificate")
        .header("Authorization", "Bearer " + NO_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON);

    String response = mockMvc.perform(get)
        .andDo(print())
        .andExpect(status().isNotFound())
        .andReturn().getResponse().getContentAsString();

    assertThat(response, containsString(
        "The request could not be completed because the credential does not exist or you do not have sufficient authorization."));
  }

  @Test
  public void getCertificateVersionsByCredentialId_returnsAllVersionsOfTheCertificateCredential() throws Exception {
    String firstResponse = generateCertificateCredential(mockMvc, "/first-certificate",
        true, "test", null, ALL_PERMISSIONS_TOKEN);
    String secondResponse = generateCertificateCredential(mockMvc, "/first-certificate",
        true, "test", null, ALL_PERMISSIONS_TOKEN);

    String firstVersion = JsonPath.parse(firstResponse).read("$.id");
    String secondVersion = JsonPath.parse(secondResponse).read("$.id");

    String response = getCertificateCredentialsByName(mockMvc, ALL_PERMISSIONS_TOKEN, "/first-certificate");

    String certificateId = JsonPath.parse(response).read("$.certificates[0].id");

    MockHttpServletRequestBuilder getVersions = get("/api/v1/certificates/" + certificateId + "/versions")
        .header("Authorization", "Bearer " + ALL_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON);

    String responseVersion = mockMvc.perform(getVersions)
        .andDo(print())
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    List<Map<String, String>> certificates = JsonPath.parse(responseVersion).read("$");

    assertThat(certificates, hasSize(2));
    assertThat(certificates.get(0).get("id"), containsString(secondVersion));
    assertThat(certificates.get(1).get("id"), containsString(firstVersion));
  }

  @Test
  public void getCertificateVersionsByCredentialId_withCurrentTrue_returnsCurrentVersionsOfTheCertificateCredential()
      throws Exception {
    String credentialName = "/test-certificate";
    generateCertificateCredential(mockMvc, credentialName, true, "test", null, ALL_PERMISSIONS_TOKEN);

    String response = getCertificateCredentialsByName(mockMvc, ALL_PERMISSIONS_TOKEN, credentialName);
    String uuid = JsonPath.parse(response)
        .read("$.certificates[0].id");

    String transitionalCertificate = JsonPath.parse(RequestHelper.regenerateCertificate(mockMvc, uuid, true, ALL_PERMISSIONS_TOKEN))
        .read("$.value.certificate");

    String nonTransitionalCertificate = JsonPath.parse(RequestHelper.regenerateCertificate(mockMvc, uuid, false, ALL_PERMISSIONS_TOKEN))
        .read("$.value.certificate");

    final MockHttpServletRequestBuilder request = get("/api/v1/certificates/" + uuid + "/versions?current=true")
        .header("Authorization", "Bearer " + AuthConstants.ALL_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON);

    response = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    JSONArray jsonArray = new JSONArray(response);

    assertThat(jsonArray.length(), equalTo(2));
    List<String> certificates = JsonPath.parse(response)
        .read("$[*].value.certificate");
    assertThat(certificates, containsInAnyOrder(transitionalCertificate, nonTransitionalCertificate));
  }

  @Test
  public void getCertificateVersionsByCredentialId_returnsError_whenUUIDIsInvalid() throws Exception {

    MockHttpServletRequestBuilder get = get("/api/v1/certificates/" + "fake-uuid" + "/versions")
        .header("Authorization", "Bearer " + ALL_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON);

    String response = mockMvc.perform(get)
        .andDo(print())
        .andExpect(status().is4xxClientError())
        .andReturn().getResponse().getContentAsString();

    assertThat(response, containsString(
        "The request could not be completed because the credential does not exist or you do not have sufficient authorization."));
  }
}
