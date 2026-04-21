package app;

import app.service.NavigationAssistantService;
import app.service.NavigationAssistantService.AssistantReply;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NavigationAssistantServiceTest {

    @Test
    void expiryQuestionRoutesDirectlyToNotifications() {
        NavigationAssistantService service = new NavigationAssistantService();

        AssistantReply reply = service.answer("restaurant-a", "How do I check my expiry alerts?");

        assertEquals("Use the Expiry Alerts page for this.", reply.shortAnswer());
        assertFalse(reply.actions().isEmpty(), "Expected at least one quick action");
        assertEquals("/notifications?tenant=restaurant-a", reply.actions().get(0).url());
    }

    @Test
    void expiryKeywordRoutesDirectlyToNotifications() {
        NavigationAssistantService service = new NavigationAssistantService();

        AssistantReply reply = service.answer("restaurant-a", "expiry");

        assertEquals("Use the Expiry Alerts page for this.", reply.shortAnswer());
        assertFalse(reply.actions().isEmpty(), "Expected at least one quick action");
        assertEquals("/notifications?tenant=restaurant-a", reply.actions().get(0).url());
    }
}
