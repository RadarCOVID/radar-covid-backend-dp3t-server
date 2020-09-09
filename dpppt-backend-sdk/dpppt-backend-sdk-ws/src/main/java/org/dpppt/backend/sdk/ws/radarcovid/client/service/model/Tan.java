/*
 * Copyright (c) 2020 Gobierno de España
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package org.dpppt.backend.sdk.ws.radarcovid.client.service.model;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

public class Tan {

    @NotNull
    @Pattern(regexp = "^\\d{12}$")
    private String tan;

    public String getTan() {
        return tan;
    }

    public void setTan(String tan) {
        this.tan = tan;
    }
}
