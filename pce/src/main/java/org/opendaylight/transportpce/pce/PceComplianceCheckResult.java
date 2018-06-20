/*
 * Copyright © 2017 AT&T and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.transportpce.pce;

public class PceComplianceCheckResult {

    private boolean result;
    private String message;

    public PceComplianceCheckResult(boolean result, String message) {
        this.result = result;
        this.message = message;
    }

    public boolean hasPassed() {
        return result;
    }

    public String getMessage() {
        return message;
    }

}
