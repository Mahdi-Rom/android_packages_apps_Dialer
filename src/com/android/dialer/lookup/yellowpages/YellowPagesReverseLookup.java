/*
 * Copyright (C) 2014 Xiao-Long Chen <chillermillerlong@hotmail.com>
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

package com.android.dialer.lookup.yellowpages;

import com.android.dialer.lookup.ReverseLookup;
import com.android.incallui.service.PhoneNumberServiceImpl.PhoneNumberInfoImpl;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class YellowPagesReverseLookup extends ReverseLookup {
    private static final String TAG =
            YellowPagesReverseLookup.class.getSimpleName();

    public YellowPagesReverseLookup(Context context) {
    }

    /**
     * Lookup image
     *
     * @param context The application context
     * @param url The image URL
     * @param data Extra data (a authentication token, perhaps)
     */
    public byte[] lookupImage(Context context, String url, Object data) {
        if (url == null) {
            throw new NullPointerException("URL is null");
        }

        Log.e(TAG, "Fetching " + url);

        HttpClient client = new DefaultHttpClient();
        HttpGet request = new HttpGet(url);

        try {
            HttpResponse response = client.execute(request);

            int responseCode = response.getStatusLine().getStatusCode();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            response.getEntity().writeTo(out);
            byte[] responseBytes = out.toByteArray();

            if (responseCode == HttpStatus.SC_OK) {
                return responseBytes;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to retrieve image", e);
        }

        return null;
    }

    /**
     * Perform phone number lookup.
     *
     * @param context The application context
     * @param normalizedNumber The normalized phone number
     * @param formattedNumber The formatted phone number
     * @param isIncoming Whether the call is incoming or outgoing
     * @return The phone number info object
     */
    public Pair<PhoneNumberInfoImpl, Object> lookupNumber(
            Context context, String normalizedNumber, String formattedNumber,
            boolean isIncoming) {
        YellowPagesApi ypa = new YellowPagesApi(normalizedNumber);
        YellowPagesApi.ContactInfo info = null;

        try {
            info = ypa.getContactInfo();
        } catch (IOException e) {
            return null;
        }

        if (info.name == null) {
            return null;
        }

        ContactBuilder builder = new ContactBuilder(
                normalizedNumber, formattedNumber);

        ContactBuilder.Name n = new ContactBuilder.Name();
        n.displayName = info.name;
        builder.setName(n);

        ContactBuilder.PhoneNumber pn = new ContactBuilder.PhoneNumber();
        pn.number = info.formattedNumber;
        pn.type = Phone.TYPE_MAIN;
        builder.addPhoneNumber(pn);

        if (info.address != null) {
            ContactBuilder.Address a = new ContactBuilder.Address();
            a.formattedAddress = info.address;
            a.type = StructuredPostal.TYPE_HOME;
            builder.addAddress(a);
        }

        ContactBuilder.WebsiteUrl w = new ContactBuilder.WebsiteUrl();
        w.url = info.website;
        w.type = Website.TYPE_PROFILE;
        builder.addWebsite(w);

        if (info.photoUrl != null) {
            builder.setPhotoUri(info.photoUrl);
        } else {
            builder.setPhotoUri(ContactBuilder.PHOTO_URI_BUSINESS);
        }

        return Pair.create(builder.build(), null);
    }
}
