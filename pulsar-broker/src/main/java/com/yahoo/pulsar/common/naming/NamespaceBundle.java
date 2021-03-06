/**
 * Copyright 2016 Yahoo Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yahoo.pulsar.common.naming;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Objects;
import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import com.yahoo.pulsar.common.naming.DestinationName;
import com.yahoo.pulsar.common.naming.NamespaceName;
import com.yahoo.pulsar.common.naming.ServiceUnitId;

public class NamespaceBundle implements ServiceUnitId, Comparable<NamespaceBundle> {
    private final NamespaceName nsname;
    private final Range<Long> keyRange;
    private final NamespaceBundleFactory factory;

    public NamespaceBundle(NamespaceName nsname, Range<Long> keyRange, NamespaceBundleFactory factory) {
        this.nsname = checkNotNull(nsname);
        this.keyRange = checkNotNull(keyRange);
        checkArgument(this.keyRange.lowerBoundType().equals(BoundType.CLOSED),
                "Invalid hash range. Lower Endpoint has to be inclusive");
        checkArgument(
                (this.keyRange.upperEndpoint().equals(NamespaceBundles.FULL_UPPER_BOUND)
                        && this.keyRange.upperBoundType().equals(BoundType.CLOSED))
                        || (!this.keyRange.upperEndpoint().equals(NamespaceBundles.FULL_UPPER_BOUND)
                                && this.keyRange.upperBoundType().equals(BoundType.OPEN)),
                "Invalid hash range. Upper Endpoint should be exclusive unless it is 0xffffffff");
        checkArgument(!this.keyRange.isEmpty(), "Cannot create bundle object for an empty key range");
        this.factory = checkNotNull(factory);
    }

    @Override
    public NamespaceName getNamespaceObject() {
        return this.nsname;
    }

    @Override
    public String toString() {
        return getKey(this.nsname, this.keyRange);
    }

    @Override
    public int compareTo(NamespaceBundle other) {
        if (this.nsname.toString().compareTo(other.nsname.toString()) != 0) {
            return this.nsname.toString().compareTo(other.nsname.toString());
        }

        if (equals(other)) {
            // completely the same range, return true
            return 0;
        }

        try {
            /**
             * <code>Range.insersection()</code> will throw <code>IllegalArgumentException</code> when two ranges are
             * not connected at all, which is a OK case for our comparison. <code>checkState</code> here is to ensure
             * that the two ranges we are comparing don't have overlaps.
             */
            checkState(this.keyRange.intersection(other.keyRange).isEmpty(),
                    "Can't compare two key ranges with non-empty intersection set");
        } catch (IllegalArgumentException iae) {
            // OK if two ranges are not connected at all
        } catch (IllegalStateException ise) {
            // It is not OK if the intersection is not empty
            throw new IllegalArgumentException(ise.getMessage(), ise);
        }

        // Now we are sure that two bundles don't have overlap
        return this.keyRange.lowerEndpoint().compareTo(other.keyRange.lowerEndpoint());
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof NamespaceBundle) {
            NamespaceBundle obj = (NamespaceBundle) other;
            return Objects.equal(this.nsname, obj.nsname)
                    && (Objects.equal(this.keyRange.lowerEndpoint(), obj.keyRange.lowerEndpoint())
                            && Objects.equal(this.keyRange.lowerBoundType(), obj.keyRange.lowerBoundType())
                            && Objects.equal(this.keyRange.upperEndpoint(), obj.keyRange.upperEndpoint())
                            && Objects.equal(this.keyRange.upperBoundType(), obj.keyRange.upperBoundType()));
        }
        return false;
    }

    @Override
    public boolean includes(DestinationName dn) {
        if (!this.nsname.equals(dn.getNamespaceObject())) {
            return false;
        }
        return this.keyRange.contains(factory.getLongHashCode(dn.toString()));
    }

    public String getBundleRange() {
        return String.format("0x%08x_0x%08x", keyRange.lowerEndpoint(), keyRange.upperEndpoint());
    }

    private static String getKey(NamespaceName nsname, Range<Long> keyRange) {
        return String.format("%s/0x%08x_0x%08x", nsname, keyRange.lowerEndpoint(), keyRange.upperEndpoint());
    }

    Range<Long> getKeyRange() {
        return this.keyRange;
    }

    Long getLowerEndpoint() {
        return this.keyRange.lowerEndpoint();
    }

    Long getUpperEndpoint() {
        return this.keyRange.upperEndpoint();
    }

    public static String getBundleRange(String namespaceBundle) {
        return namespaceBundle.substring(namespaceBundle.lastIndexOf('/') + 1);
    }
}
