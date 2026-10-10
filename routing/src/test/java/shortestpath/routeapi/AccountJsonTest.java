package shortestpath.routeapi;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import shortestpath.accounts.Account;
import shortestpath.accounts.AccountCompiler;
import shortestpath.accounts.ClientState;
import shortestpath.accounts.canonical.CanonicalAccounts;

public class AccountJsonTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void canonicalAccountsSurviveTheJsonRoundTrip() throws Exception {
        for (String name : CanonicalAccounts.NAMES) {
            Account account = CanonicalAccounts.account(name);
            String json = MAPPER.writeValueAsString(AccountJson.of(name, account));
            ClientState expected = AccountCompiler.compile(account);
            ClientState actual = AccountCompiler.compile(MAPPER.readValue(json, AccountJson.class).toAccount());
            assertEquals(name, expected.varbits(), actual.varbits());
            assertEquals(name, expected.varplayers(), actual.varplayers());
            assertEquals(name, expected.questStates(), actual.questStates());
            assertEquals(name, expected.levels(), actual.levels());
            assertEquals(name, expected.totalLevel(), actual.totalLevel());
            assertEquals(name, expected.inventory(), actual.inventory());
            assertEquals(name, expected.equipment(), actual.equipment());
            assertEquals(name, expected.bank(), actual.bank());
            assertEquals(name, expected.plantedSpiritTrees(), actual.plantedSpiritTrees());
            assertEquals(name, expected.nowMinutes(), actual.nowMinutes());
            Account roundTripped = MAPPER.readValue(json, AccountJson.class).toAccount();
            assertEquals(name, account.poh().portals, roundTripped.poh().portals);
            assertEquals(name, account.poh().jewelleryBox, roundTripped.poh().jewelleryBox);
            assertEquals(name, account.poh().mountedXerics, roundTripped.poh().mountedXerics);
        }
    }
}
