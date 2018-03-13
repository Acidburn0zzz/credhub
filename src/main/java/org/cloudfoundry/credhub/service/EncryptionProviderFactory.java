package org.cloudfoundry.credhub.service;

import org.cloudfoundry.credhub.config.EncryptionKeyProvider;
import org.cloudfoundry.credhub.config.EncryptionKeysConfiguration;
import org.cloudfoundry.credhub.config.LunaProviderProperties;
import org.cloudfoundry.credhub.util.TimedRetry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Component
public class EncryptionProviderFactory {

  private EncryptionKeysConfiguration encryptionKeysConfiguration;
  private LunaProviderProperties lunaProviderProperties;
  private TimedRetry timedRetry;
  private PasswordKeyProxyFactory passwordKeyProxyFactory;
  private HashMap<String, InternalEncryptionService> map;

  @Autowired
  public EncryptionProviderFactory(EncryptionKeysConfiguration keysConfiguration,
      LunaProviderProperties lunaProviderProperties, TimedRetry timedRetry,
      PasswordKeyProxyFactory passwordKeyProxyFactory) throws Exception {
    this.encryptionKeysConfiguration = keysConfiguration;
    this.lunaProviderProperties = lunaProviderProperties;
    this.timedRetry = timedRetry;
    this.passwordKeyProxyFactory = passwordKeyProxyFactory;
    map = new HashMap<>();
  }

  public InternalEncryptionService getEncryptionService(EncryptionKeyProvider provider) throws Exception {
    InternalEncryptionService encryptionService;

    if (map.containsKey(provider.getProviderName())) {
      return map.get(provider.getProviderName());
    } else {
      switch (provider.getProviderType()) {
        case HSM:
          encryptionService = new LunaEncryptionService(new LunaConnection(lunaProviderProperties),
              encryptionKeysConfiguration.isKeyCreationEnabled(),
              timedRetry);
          break;
//        case EXTERNAL:
//          encryptionService = new ExternalEncryptionProvider();
        default:
          encryptionService = new PasswordEncryptionService(passwordKeyProxyFactory);
      }
      map.put(provider.getProviderName(), encryptionService);
      return encryptionService;
    }
  }
}
