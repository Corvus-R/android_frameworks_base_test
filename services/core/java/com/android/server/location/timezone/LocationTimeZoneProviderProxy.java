/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.location.timezone;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.Handler;
import android.util.IndentingPrintWriter;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.location.timezone.LocationTimeZoneEvent;
import com.android.internal.location.timezone.LocationTimeZoneProviderRequest;
import com.android.server.timezonedetector.Dumpable;

import java.util.Objects;

/**
 * System server-side proxy for ILocationTimeZoneProvider implementations, i.e. this provides the
 * system server object used to communicate with a remote LocationTimeZoneProvider over Binder,
 * which could be running in a different process. As LocationTimeZoneProviders are bound / unbound
 * this proxy will rebind to the "best" available remote process.
 *
 * <p>Threading guarantees provided / required by this interface:
 * <ul>
 *     <li>All public methods defined by this class must be invoked using the {@link Handler} thread
 *     from the {@link ThreadingDomain} passed to the constructor, excluding
 *     {@link #dump(IndentingPrintWriter, String[])}</li>
 *     <li>Non-static public methods that make binder calls to remote processes (e.g.
 *     {@link #setRequest(LocationTimeZoneProviderRequest)}) are executed asynchronously and will
 *     return immediately.</li>
 *     <li>Callbacks received via binder are delivered via {@link Listener} are delivered on the
 *     {@link Handler} thread from the {@link ThreadingDomain} passed to the constructor.
 * </ul>
 *
 * <p>This class exists to enable the introduction of test implementations of {@link
 * LocationTimeZoneProviderProxy} that can be used when a device is in a test mode to inject test
 * events / behavior that are otherwise difficult to simulate.
 */
abstract class LocationTimeZoneProviderProxy implements Dumpable {

    @NonNull protected final Context mContext;
    @NonNull protected final ThreadingDomain mThreadingDomain;
    @NonNull protected final Object mSharedLock;

    // Non-null and effectively final after setListener() is called.
    @GuardedBy("mSharedLock")
    @Nullable
    protected Listener mListener;

    LocationTimeZoneProviderProxy(
            @NonNull Context context, @NonNull ThreadingDomain threadingDomain) {
        mContext = Objects.requireNonNull(context);
        mThreadingDomain = Objects.requireNonNull(threadingDomain);
        mSharedLock = threadingDomain.getLockObject();
    }

    /**
     * Initializes the proxy. The supplied listener can expect to receive all events after this
     * point. This method also calls {@link #onInitialize()} for subclasses to handle their own
     * initialization.
     */
    void initialize(@NonNull Listener listener) {
        Objects.requireNonNull(listener);
        synchronized (mSharedLock) {
            if (mListener != null) {
                throw new IllegalStateException("listener already set");
            }
            this.mListener = listener;
        }
        onInitialize();
    }

    /**
     * Initializes the proxy. This is called after {@link #mListener} is set.
     */
    abstract void onInitialize();

    /**
     * Sets a new request for the provider.
     */
    abstract void setRequest(@NonNull LocationTimeZoneProviderRequest request);

    /**
     * Handles a {@link LocationTimeZoneEvent} from a remote process.
     */
    final void handleLocationTimeZoneEvent(
            @NonNull LocationTimeZoneEvent locationTimeZoneEvent) {
        // These calls are invoked on a binder thread. Move to the mThreadingDomain thread as
        // required by the guarantees for this class.
        mThreadingDomain.post(() -> mListener.onReportLocationTimeZoneEvent(locationTimeZoneEvent));
    }

    /**
     * Interface for listening to location time zone providers. See {@link
     * LocationTimeZoneProviderProxy} for threading guarantees.
     */
    interface Listener {

        /**
         * Called when a provider receives a {@link LocationTimeZoneEvent}.
         */
        void onReportLocationTimeZoneEvent(@NonNull LocationTimeZoneEvent locationTimeZoneEvent);

        /**
         * Called when a provider is (re)bound.
         */
        void onProviderBound();

        /** Called when a provider is unbound. */
        void onProviderUnbound();
    }
}
