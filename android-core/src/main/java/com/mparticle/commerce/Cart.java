package com.mparticle.commerce;


import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.mparticle.BaseEvent;
import com.mparticle.MParticle;
import com.mparticle.identity.MParticleUser;
import com.mparticle.internal.ConfigManager;
import com.mparticle.internal.Logger;
import com.mparticle.internal.listeners.ApiClass;
import com.mparticle.internal.listeners.InternalListenerManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @deprecated use {@link CommerceEvent} in conjunction with {@link MParticle#logEvent(BaseEvent)} to
 * track commerce related events
 *
 * The Cart has a one-to-one relationship with MParticleUsers.
 * <p></p>
 * The Cart will persist state across app-restarts.
 * <p></p>
 * You may access the cart via the {@link MParticleUser} object:
 * <p></p>
 * <pre>
 * {@code
 * MParticle.getInstance().Identity().getCurrentUser().cart()}
 * </pre>
 * <p></p>
 * You should not instantiate this class directly
 * <p></p>
 */
@ApiClass
@Deprecated
public final class Cart {

    private final List<Product> productList;
    public final static int DEFAULT_MAXIMUM_PRODUCT_COUNT = 30;
    private static int MAXIMUM_PRODUCT_COUNT = DEFAULT_MAXIMUM_PRODUCT_COUNT;
    private long userId;
    private Context mContext;

    @Deprecated
    public Cart(@NonNull Context context, long userId) {
        mContext = context;
        productList = new LinkedList<Product>();
        this.userId = userId;
        loadCart(ConfigManager.getUserStorage(context, this.userId).getSerializedCart());
    }

    /**
     * Set the maximum product count to hold in the cart. On memory constrained devices/apps,
     * this value can be lowered to avoid possible memory exceptions.
     *
     * @param maximum
     */
    @Deprecated
    public static void setMaximumProductCount(final int maximum) {
        MAXIMUM_PRODUCT_COUNT = maximum;
    }

    /**
     * Reset the cart and re-populate it from a String representation. This method allows you to support
     * multiple carts per user.
     *
     * @param cartJson a JSON-encoded string acquired from {@link #toString()}
     */
    @Deprecated
    public synchronized void loadFromString(@NonNull String cartJson) {
        loadCart(cartJson);
    }

    /**
     * In addition to providing a human-readable JSON representation of the cart, the output
     * of this method can be stored and then later passed into {@link #loadFromString(String)} to
     * support multiple-cart use-cases.
     *
     * @return a JSON representation of the Cart
     * @see #loadFromString(String)
     */
    @Override
    @NonNull
    public synchronized String toString() {
        JSONObject cartJsonObject = new JSONObject();
        JSONArray products = new JSONArray();
        if (productList.size() > 0) {
            for (int i = 0; i < productList.size(); i++) {
                products.put(productList.get(i).toJson());
            }
            try {
                cartJsonObject.put("pl", products);
            } catch (JSONException e) {

            }
        }
        return cartJsonObject.toString();
    }

    /**
     * Remove all Products from the Cart. This will not log an event.
     *
     * @return the Cart object for method chaining
     */
    @Deprecated
    @NonNull
    public synchronized Cart clear() {
        productList.clear();
        save();
        return this;
    }

    private synchronized void save() {
        String serializedCart = toString();
        ConfigManager.getUserStorage(mContext, this.userId).setSerializedCart(serializedCart);
    }

    /**
     * Find a product by name
     *
     * @param name the product name
     * @return a Product object, or null if no matching Products were found.
     */
    @Deprecated
    @Nullable
    public synchronized Product getProduct(@Nullable String name) {
        for (int i = 0; i < productList.size(); i++) {
            if (productList.get(i).getName() != null && productList.get(i).getName().equalsIgnoreCase(name)) {
                return productList.get(i);
            }
        }
        return null;
    }

    /**
     * Retrieve the current list of Products in the Cart.
     * <p></p>
     * Note that this returns an {@code UnmodifiableCollection} that will throw an {@code UnsupportedOperationException}
     * if you attempt to add or remove Products.
     *
     * @return an {@code UnmodifiableCollection} of Products in the Cart
     */
    @Deprecated
    @NonNull
    public List<Product> products() {
        return Collections.unmodifiableList(productList);
    }

    /**
     * @deprecated use {@link CommerceApi#} {@link CommerceEvent} with the {@link Product#REMOVE_FROM_CART} action with the {@link MParticle#logEvent(BaseEvent)} api instead
     *
     * Remove one or more products from the Cart and log a {@link CommerceEvent}.
     * <p></p>
     * This method will log a {@link CommerceEvent} with the {@link Product#REMOVE_FROM_CART} action.
     * <p></p>
     * If the Cart already contains a Product that is considered equal, the Product will be removed.
     *
     * @param product the product objects to remove from the Cart
     * @return the Cart object, useful for chaining several commands
     *
     */
    @Deprecated
    @NonNull
    public synchronized Cart remove(@NonNull Product product) {
        return remove(product, true);
    }

    /**
     * @deprecated use {@link CommerceApi#} {@link CommerceEvent} with the {@link Product#REMOVE_FROM_CART} action with the {@link MParticle#logEvent(BaseEvent)} api instead
     *
     * Remove one or more products from the Cart and log a {@link CommerceEvent}.
     * <p></p>
     * This method will log a {@link CommerceEvent} with the {@link Product#REMOVE_FROM_CART} action.
     * <p></p>
     * If the Cart already contains a Product that is considered equal, the Product will be removed.
     *
     * @param product the product to remove from the Cart
     * @return the Cart object, useful for chaining several commands
     *
     */
    @Deprecated
    @NonNull
    public synchronized Cart remove(@NonNull Product product, boolean logEvent) {
        if (product != null && productList.remove(product)) {
            save();
        }
        if (logEvent) {
            CommerceEvent event = new CommerceEvent.Builder(Product.REMOVE_FROM_CART, product).build();
            MParticle.getInstance().logEvent(event);
        }
        return this;
    }

    /**
     * @deprecated use {@link CommerceApi#} {@link CommerceEvent} with the {@link Product#REMOVE_FROM_CART} action with the {@link MParticle#logEvent(BaseEvent)} api instead
     *
     * Remove a product from the Cart by index and log a {@link CommerceEvent}.
     * <p></p>
     * This method will log a {@link CommerceEvent} with the {@link Product#REMOVE_FROM_CART} action.
     *
     * @param index of the Product to remove
     * @return boolean determining if a product was actually removed.
     * @see #products()
     */
    @Deprecated
    public synchronized boolean remove(int index) {
        boolean removed = false;
        if (index >= 0 && productList.size() > index) {
            Product product = productList.remove(index);
            save();
            CommerceEvent event = new CommerceEvent.Builder(Product.REMOVE_FROM_CART, product)
                    .build();
            MParticle.getInstance().logEvent(event);
        }
        return removed;
    }

    /**
     * @deprecated use {@link CommerceApi#} {@link CommerceEvent} with the {@link Product#ADD_TO_CART} action with the {@link MParticle#logEvent(BaseEvent)} api instead
     *
     * Add one or more products to the Cart and optionally log a {@link CommerceEvent}.
     * <p></p>
     * This method will log a {@link CommerceEvent} with the {@link Product#ADD_TO_CART} action based on the logEvent parameter. Products added here
     * will remain in the cart across app restarts, and will be included in future calls to {@link Cart#purchase(TransactionAttributes)}
     * or {@link CommerceEvent}'s with a product action {@link Product#PURCHASE}
     * <p></p>
     *
     * @param newProducts the products to add to the Cart
     * @return the Cart object, useful for chaining several commands
     *
     */
    @Deprecated
    @NonNull
    public synchronized Cart addAll(@NonNull List<Product> newProducts, boolean logEvent) {
        if (newProducts != null && newProducts.size() > 0 && productList.size() < MAXIMUM_PRODUCT_COUNT) {
            for (Product product : newProducts) {
                if (product != null && !productList.contains(product)){
                    product.updateTimeAdded();
                    productList.add(product);
                    save();

                }
            }
            if (logEvent) {
                MParticle.getInstance().logEvent(
                        new CommerceEvent.Builder(Product.ADD_TO_CART, newProducts.get(0)).products(newProducts).build()
                );
            }
        }
        return this;
    }

    /**
     * @deprecated use {@link CommerceApi#} {@link CommerceEvent} with the {@link Product#ADD_TO_CART} action with the {@link MParticle#logEvent(BaseEvent)} api instead
     *
     * Add one or more products to the Cart and log a {@link CommerceEvent}.
     * <p></p>
     * This method will log a {@link CommerceEvent} with the {@link Product#ADD_TO_CART} action. Products added here
     * will remain in the cart across app restarts, and will be included in future calls to {@link Cart#purchase(TransactionAttributes)}
     * or {@link CommerceEvent}'s with a product action {@link Product#PURCHASE}
     * <p></p>
     * If the Cart already contains a Product that is considered equal, this method is a no-op.
     *
     * @param product the product to add to the Cart
     * @return the Cart object, useful for chaining several commands
     *
     */
    @Deprecated
    @NonNull
    public synchronized Cart add(@NonNull Product product) {
        return add(product, true);
    }

    /**
     * @deprecated use {@link CommerceApi#} {@link CommerceEvent} with the {@link Product#ADD_TO_CART} action with the {@link MParticle#logEvent(BaseEvent)} api instead
     *
     * Add one or more products to the Cart and log a {@link CommerceEvent}.
     * <p></p>
     * This method will log a {@link CommerceEvent} with the {@link Product#ADD_TO_CART} action. Products added here
     * will remain in the cart across app restarts, and will be included in future calls to {@link Cart#purchase(TransactionAttributes)}
     * or {@link CommerceEvent}'s with a product action {@link Product#PURCHASE}
     * <p></p>
     * If the Cart already contains a Product that is considered equal, this method is a no-op.
     *
     * @param product the product to add to the Cart
     * @return the Cart object, useful for chaining several commands
     *
     */
    @Deprecated
    @NonNull
    public synchronized Cart add(@NonNull Product product, boolean logEvent) {
        if (product != null && productList.size() < MAXIMUM_PRODUCT_COUNT && !productList.contains(product)) {
            product.updateTimeAdded();
            productList.add(product);
            save();
            if (logEvent) {
                CommerceEvent event = new CommerceEvent.Builder(Product.ADD_TO_CART, product).build();
                InternalListenerManager.getListener().onCompositeObjects(product, event);
                MParticle.getInstance().logEvent(event);
            }
        }
        return this;
    }

    /**
     * @deprecated use {@link CommerceApi#} {@link CommerceEvent} with the {@link Product#CHECKOUT} action with the {@link MParticle#logEvent(BaseEvent)} api instead
     *
     * Log a {@link CommerceEvent} with the {@link Product#CHECKOUT} action, including the Products that are
     * currently in the Cart.
     *
     * You should call {@link Cart#add(Product)} prior to this method.
     *
     * @param step the checkout progress/step for apps that have a multi-step checkout process
     * @param options a label to associate with the checkout event
     */
    @Deprecated
    public synchronized void checkout(int step, @Nullable String options) {
        if (productList != null && productList.size() > 0) {
            CommerceEvent event = new CommerceEvent.Builder(Product.CHECKOUT, productList.get(0))
                    .checkoutStep(step)
                    .checkoutOptions(options)
                    .products(productList)
                    .build();
            InternalListenerManager.getListener().onCompositeObjects(options, event);
            MParticle.getInstance().logEvent(event);
        } else {
            Logger.error("checkout() called but there are no Products in the Cart, no event was logged.");

        }
    }

    /**
     * @deprecated use {@link CommerceApi#} {@link CommerceEvent} with the {@link Product#CHECKOUT} action with the {@link MParticle#logEvent(BaseEvent)} api instead
     *
     * Log a {@link CommerceEvent} with the {@link Product#CHECKOUT} action, including the Products that are
     * currently in the Cart.
     *
     * You should call {@link Cart#add(Product)} prior to this method.
     *
     */
    @Deprecated
    public synchronized void checkout() {
        InternalListenerManager.getListener().onApiCalled("checkout");
        if (productList != null && productList.size() > 0) {
            CommerceEvent event = new CommerceEvent.Builder(Product.CHECKOUT, productList.get(0))
                    .products(productList)
                    .build();
            InternalListenerManager.getListener().onCompositeObjects("checkout", event);
            MParticle.getInstance().logEvent(event);
        }else {
            Logger.error("checkout() called but there are no Products in the Cart, no event was logged.");
        }
    }

    /**
     * @deprecated use {@link CommerceApi#} {@link CommerceEvent} with the {@link Product#PURCHASE} action with the {@link MParticle#logEvent(BaseEvent)} api instead
     *
     * Log a {@link CommerceEvent} with the {@link Product#PURCHASE} action for the Products that are
     * currently in the Cart.
     *
     * By default, this method will *not* clear the cart. You must manually call {@link Cart#clear()}.
     *
     * You should call {@link Cart#add(Product)} prior to this method.
     *
     * @param attributes the attributes to associate with this purchase
     */
    @Deprecated
    public void purchase(@NonNull TransactionAttributes attributes) {
        purchase(attributes, false);
    }

    /**
     * @deprecated use {@link CommerceApi#} {@link CommerceEvent} with the {@link Product#PURCHASE} action with the {@link MParticle#logEvent(BaseEvent)} api instead
     *
     * Log a {@link CommerceEvent} with the {@link Product#PURCHASE} action for the Products that are
     * currently in the Cart.
     *
     * You should call {@link Cart#add(Product)} prior to this method.
     *
     * @param attributes the attributes to associate with this purchase
     * @param clearCart boolean determining if the cart should remove its contents after the purchase
     */
    @Deprecated
    public synchronized void purchase(@NonNull TransactionAttributes attributes, boolean clearCart) {
        if (productList != null && productList.size() > 0) {
            CommerceEvent event = new CommerceEvent.Builder(Product.PURCHASE, productList.get(0))
                    .products(productList)
                    .transactionAttributes(attributes)
                    .build();
            if (clearCart) {
                clear();
            }
            InternalListenerManager.getListener().onCompositeObjects(attributes, event);
            MParticle.getInstance().logEvent(event);
        }else {
            Logger.error("purchase() called but there are no Products in the Cart, no event was logged.");
        }
    }

    /**
     * @deprecated use {@link CommerceApi#} {@link CommerceEvent} with the {@link Product#REFUND} action with the {@link MParticle#logEvent(BaseEvent)} api instead
     *
     * Log a {@link CommerceEvent} with the {@link Product#REFUND} action for the Products that are
     * currently in the Cart.
     *
     * @param attributes the attributes to associate with this refund. Typically at least the transaction ID is required.
     */
    @Deprecated
    public void refund(@NonNull TransactionAttributes attributes, boolean clearCart) {
        if (productList != null && productList.size() > 0) {
            CommerceEvent event = new CommerceEvent.Builder(Product.REFUND, productList.get(0))
                    .products(productList)
                    .transactionAttributes(attributes)
                    .build();
            if (clearCart) {
                clear();
            }
            MParticle.getInstance().logEvent(event);
            InternalListenerManager.getListener().onCompositeObjects(attributes, event);
        } else {
            Logger.error("refund() called but there are no Products in the Cart, no event was logged.");
        }
    }

    private void loadCart(@NonNull String cartJson) {
        if (cartJson != null) {
            try {
                JSONObject cartJsonObject = new JSONObject(cartJson);
                JSONArray products = cartJsonObject.getJSONArray("pl");
                clear();
                for (int i = 0; i < products.length() && i < MAXIMUM_PRODUCT_COUNT; i++) {
                    productList.add(Product.fromJson(products.getJSONObject(i)));
                }
                save();
            } catch (JSONException jse) {

            }
        }
    }
}