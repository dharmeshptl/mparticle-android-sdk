package com.mparticle.kits;

/**
 * Implement this interface and react to deep links
 */
public interface DeepLinkListener {
    void onResult(DeepLinkResult result);
    void onError(DeepLinkError error);
}