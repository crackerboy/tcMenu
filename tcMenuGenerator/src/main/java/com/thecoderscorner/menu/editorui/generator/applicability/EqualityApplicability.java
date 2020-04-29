/*
 * Copyright (c)  2016-2020 https://www.thecoderscorner.com (Nutricherry LTD).
 * This product is licensed under an Apache license, see the LICENSE file in the top-level directory.
 *
 */

package com.thecoderscorner.menu.editorui.generator.applicability;

import com.thecoderscorner.menu.pluginapi.CreatorProperty;

import java.util.List;

public class EqualityApplicability implements CodeApplicability {
    private final String propertyId;
    private final String value;
    private final boolean invert;

    public EqualityApplicability(String propertyId, String value, boolean invert)
    {
        this.propertyId = propertyId;
        this.value = value;
        this.invert = invert;
    }

    @Override
    public boolean isApplicable(List<CreatorProperty> properties)
    {
        var prop = properties.stream().filter(p -> p.getName().equals(propertyId))
            .map(CreatorProperty::getLatestValue)
            .findFirst().orElse("");

        var match = prop.equals(value);
        return invert ? !match : match;
    }

    @Override
    public String toString() {
        return "EqualityApplicability{" +
                "propertyId='" + propertyId + '\'' +
                ", value='" + value + '\'' +
                ", invert=" + invert +
                '}';
    }
}
