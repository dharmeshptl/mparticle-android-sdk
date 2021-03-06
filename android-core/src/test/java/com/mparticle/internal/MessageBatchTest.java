package com.mparticle.internal;

import com.mparticle.MParticle;
import com.mparticle.commerce.CommerceApi;
import com.mparticle.consent.ConsentState;
import com.mparticle.consent.GDPRConsent;
import com.mparticle.mock.MockContext;
import com.mparticle.mock.MockSharedPreferences;

import org.json.JSONObject;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;

public class MessageBatchTest {


    @Test
    public void testCreate() throws Exception {
        MParticle mockMp = Mockito.mock(MParticle.class);
        Mockito.when(mockMp.getEnvironment()).thenReturn(MParticle.Environment.Development);
        CommerceApi mockCommerce = Mockito.mock(CommerceApi.class);
        Mockito.when(mockMp.Commerce()).thenReturn(mockCommerce);
        MParticle.setInstance(mockMp);
        ConfigManager manager = new ConfigManager(new MockContext(), MParticle.Environment.Production, "some api key", "some api secret");
        boolean sessionHistory = true;
        BatchId batchId = new BatchId(manager.getMpid(), null, null, null);
        MessageBatch batch = MessageBatch.create( sessionHistory, manager,new JSONObject(), batchId);
        assertNotNull(batch.getString("dt"));
        assertNotNull(batch.getString("id"));
        assertNotNull(batch.getDouble("ct"));
        assertNotNull(batch.getString("sdk"));
        assertNotNull(batch.getBoolean("oo"));
        assertNotNull(batch.getDouble("uitl"));
        assertNotNull(batch.getDouble("stl"));
        assertNotNull(batch.getJSONObject("ck"));
        if (manager.getProviderPersistence() != null) {
            assertNotNull(batch.getJSONObject("cms"));
        }
        sessionHistory = false;
        batch = MessageBatch.create( sessionHistory, manager,new JSONObject(), batchId);
        assertNotNull(batch.getString("dt"));
        assertNotNull(batch.getString("id"));
        assertNotNull(batch.getDouble("ct"));
        assertNotNull(batch.getString("sdk"));
        assertNotNull(batch.getBoolean("oo"));
        assertNotNull(batch.getDouble("uitl"));
        assertNotNull(batch.getDouble("stl"));
        assertNotNull(batch.getJSONObject("ck"));
        if (manager.getProviderPersistence() != null) {
            assertNotNull(batch.getJSONObject("cms"));
        }

        batch = MessageBatch.create( sessionHistory, manager,new JSONObject(), batchId);
        assertFalse(batch.has("pb"));
    }

    @Test
    public void testAddConsentState() throws Exception {
        MParticle mockMp = Mockito.mock(MParticle.class);
        Mockito.when(mockMp.getEnvironment()).thenReturn(MParticle.Environment.Development);
        CommerceApi mockCommerce = Mockito.mock(CommerceApi.class);
        Mockito.when(mockMp.Commerce()).thenReturn(mockCommerce);
        MParticle.setInstance(mockMp);
        ConfigManager manager = new ConfigManager(new MockContext(), MParticle.Environment.Production, "some api key", "some api secret");
        boolean sessionHistory = true;
        BatchId batchId = new BatchId(manager.getMpid(), null, null, null);
        MessageBatch batch = MessageBatch.create( sessionHistory, manager,new JSONObject(), batchId);
        batch.addConsentState(null);
        batch.addConsentState(ConsentState.builder().build());
        JSONObject consent = batch.optJSONObject("con");
        assertNotNull(consent);
        batch.addConsentState(
                ConsentState.builder().addGDPRConsentState("foo purpose",
                        GDPRConsent.builder(true)
                                .timestamp(10L)
                                .location("foo location")
                                .hardwareId("foo hardware id")
                                .document("foo document")
                                .build())
                        .build()
        );
        consent = batch.optJSONObject(Constants.MessageKey.CONSENT_STATE);
        assertNotNull(consent);
        consent = consent.optJSONObject(Constants.MessageKey.CONSENT_STATE_GDPR);
        assertNotNull(consent);
        consent = consent.getJSONObject("foo purpose");
        assertNotNull(consent);
        assertEquals(true, consent.getBoolean(Constants.MessageKey.CONSENT_STATE_GDPR_CONSENTED));
        assertEquals((long)10, consent.getLong(Constants.MessageKey.CONSENT_STATE_GDPR_TIMESTAMP));
        assertEquals("foo location", consent.getString(Constants.MessageKey.CONSENT_STATE_GDPR_LOCATION));
        assertEquals("foo hardware id", consent.getString(Constants.MessageKey.CONSENT_STATE_GDPR_HARDWARE_ID));
        assertEquals("foo document", consent.getString(Constants.MessageKey.CONSENT_STATE_GDPR_DOCUMENT));
    }
}