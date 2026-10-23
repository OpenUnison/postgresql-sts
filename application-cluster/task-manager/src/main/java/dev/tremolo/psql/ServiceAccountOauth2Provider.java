package dev.tremolo.psql;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.postgresql.plugin.OAuthTokenProvider;
import org.postgresql.plugin.OAuthTokenRequest;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.postgresql.util.URLCoder;

public class ServiceAccountOauth2Provider implements OAuthTokenProvider {
  @Override
  public char[] getToken(OAuthTokenRequest request) throws PSQLException {
      
    try {
      System.out.println("*************GETTING OAUTH2 TOKEN****************");
      return Files.readString(Path.of("/tokens/token.jwt"), StandardCharsets.UTF_8).toCharArray();
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  public ServiceAccountOauth2Provider(Properties info) {

  }
}
