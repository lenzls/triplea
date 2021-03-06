package org.triplea.server.remote.actions;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.github.database.rider.core.api.dataset.DataSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.triplea.http.client.IpAddressParser;
import org.triplea.http.client.remote.actions.RemoteActionsClient;
import org.triplea.server.http.AllowedUserRole;
import org.triplea.server.http.ProtectedEndpointTest;

class RemoteActionsControllerIntegrationTest extends ProtectedEndpointTest<RemoteActionsClient> {

  RemoteActionsControllerIntegrationTest() {
    super(RemoteActionsClient::new);
  }

  @Test
  @DataSet(cleanBefore = true, value = "integration.yml")
  void sendShutdownSignal() {
    verifyEndpoint(
        AllowedUserRole.MODERATOR,
        client -> client.sendShutdownRequest(IpAddressParser.fromString("99.99.33.33")));
  }

  @Test
  @DisplayName("IP address is banned")
  void userIsBanned() {
    final boolean result =
        verifyEndpointReturningObject(
            AllowedUserRole.HOST,
            client -> client.checkIfPlayerIsBanned(IpAddressParser.fromString("1.1.1.1")));

    assertThat(result, is(true));
  }

  @Test
  @DisplayName("IP address has an expired ban")
  void userWasBanned() {
    final boolean result =
        verifyEndpointReturningObject(
            AllowedUserRole.HOST,
            client -> client.checkIfPlayerIsBanned(IpAddressParser.fromString("1.1.1.2")));

    assertThat(result, is(false));
  }

  @Test
  @DisplayName("IP address is not in ban table at all")
  void userWasNeverBanned() {
    final boolean result =
        verifyEndpointReturningObject(
            AllowedUserRole.HOST,
            client -> client.checkIfPlayerIsBanned(IpAddressParser.fromString("1.1.1.3")));

    assertThat(result, is(false));
  }
}
