package org.cloudfoundry.credhub.controller.v1;

import org.cloudfoundry.credhub.CredentialManagerApp;
import org.cloudfoundry.credhub.handler.RegenerateHandler;
import org.cloudfoundry.credhub.util.AuthConstants;
import org.cloudfoundry.credhub.util.DatabaseProfileResolver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@ActiveProfiles(value = "unit-test", resolver = DatabaseProfileResolver.class)
@SpringBootTest(classes = CredentialManagerApp.class)
@Transactional
public class RegenerateControllerTest {

  @Autowired
  private WebApplicationContext webApplicationContext;

  @MockBean
  private RegenerateHandler regenerateHandler;

  private MockMvc mockMvc;

  @Before
  public void beforeEach() {
    mockMvc = MockMvcBuilders
        .webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();
  }

  @Test
  public void POST_regeneratesThePassword() throws Exception {
    mockMvc.perform(makeRegenerateRequest()).andDo(print()).andExpect(status().isOk());

    Mockito.verify(regenerateHandler).handleRegenerate(eq("/picard"));
  }

  @Test
  public void POST_withSignedBy_regeneratesAllCertificatesSignedByCA() throws Exception {
    mockMvc.perform(makeBulkRegenerateRequest()).andDo(print()).andExpect(status().isOk());
  }

  private MockHttpServletRequestBuilder makeRegenerateRequest() {
    return post("/api/v1/regenerate")
        .header("Authorization", "Bearer " + AuthConstants.ALL_PERMISSIONS_TOKEN)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\n"
            + "  \"name\" : \"picard\"\n"
            + "}");
  }

  private MockHttpServletRequestBuilder makeBulkRegenerateRequest() {
    return post("/api/v1/bulk-regenerate")
        .header("Authorization", "Bearer " + AuthConstants.ALL_PERMISSIONS_TOKEN)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\n"
            + "  \"signed_by\" : \"/some-ca\"\n"
            + "}");
  }
}
