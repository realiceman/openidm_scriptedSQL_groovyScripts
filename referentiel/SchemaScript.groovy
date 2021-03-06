/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Version 1.0
 * Author youssef
 */
package org.forgerock.openicf.connectors.referentiel

import org.forgerock.openicf.connectors.referentiel.REFERENTIELConfiguration
import org.forgerock.openicf.misc.scriptedcommon.OperationType
import org.identityconnectors.common.logging.Log
import org.forgerock.openicf.misc.scriptedcommon.ICFObjectBuilder

import static org.identityconnectors.framework.common.objects.AttributeInfo.Flags.REQUIRED

/**
 * Built-in accessible objects
 **/

// OperationType is SCHEMA for this script
def operation = operation as OperationType

// The configuration class created specifically for this connector
def configuration = configuration as REFERENTIELConfiguration

// Default logging facility
def log = log as Log

// The schema builder object
def builder = builder as ICFObjectBuilder

/**
 * Script action - Customizable
 *
 * Build the schema for this connector that describes what the ICF client will see.  The schema
 * might be statically built or may be built from data retrieved from the external source.
 *
 * This script should use the builder object to create the schema.
 **/

/* Log something to demonstrate this script executed */
log.info("Schema script, operation = " + operation.toString());

builder.schema({
    objectClass {
        type ObjectClass.GROUP_NAME
        
    }
    objectClass {
        type ObjectClass.ACCOUNT_NAME
        
    }
    
})
