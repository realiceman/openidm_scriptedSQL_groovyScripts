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
import org.identityconnectors.framework.common.objects.Uid
import org.forgerock.openicf.misc.scriptedcommon.MapFilterVisitor
import org.identityconnectors.framework.common.objects.AttributeBuilder
import org.identityconnectors.framework.common.objects.ObjectClass
import org.identityconnectors.framework.common.objects.OperationOptions
import org.identityconnectors.framework.common.objects.SearchResult
import org.identityconnectors.framework.common.objects.filter.Filter

/**
 * Built-in accessible objects
 **/

// OperationType is SEARCH for this script
def operation = operation as OperationType

// The configuration class created specifically for this connector
def configuration = configuration as REFERENTIELConfiguration

// Default logging facility
def log = log as Log

// The objectClass of the object to be searched, e.g. ACCOUNT or GROUP
def objectClass = objectClass as ObjectClass

// The search filter for this operation
def filter = filter as Filter

// Additional options for this operation
def options = options as OperationOptions

def connection = connection as Connection
def ORG = new ObjectClass("organization")


log.info("[referentielScriptedSQL] Entering " + operation + " Script");

def sql = new Sql(connection);
def where = " WHERE 1=1 ";
def whereParams = []

// Set where and whereParams if they have been passed in the request for paging
if (options.pagedResultsCookie != null) {
    def cookieProps = options.pagedResultsCookie.split(",");
    if (cookieProps.size() != 2) {
        throw new BadRequestException("Expecting pagedResultsCookie to contain timestamp and id.");
    }
    // The timestamp and id are for this example only.
    // The user can use their own properties to sort on.
    // For paging it is important that the properties that you use must identify
    // a distinct set of pages for each iteration of the
    // pagedResultsCookie, which can be decided by last record of the previous set.
    where =  " WHERE customerNumber > ? "
    whereParams = [ cookieProps[0], cookieProps[1].toInteger()]
}

// Determine what properties will be used to sort the query
def orderBy =  ["customerNumber"]
if (options.sortKeys != null && options.sortKeys.size() > 0) {
    options.sortKeys.each {
        def key = it.toString();
        if (key.substring(0,1) == "+") {
            orderBy.add(key.substring(1,key.size()) + " ASC")
        } else {
            orderBy.add(key.substring(1,key.size()) + " DESC")
        }
    }
    orderBy = " ORDER BY " + orderBy.join(",")
} else {
    orderBy = ""
}

def limit = ""
if (options.pageSize != null) {
    limit = " LIMIT " + options.pageSize.toString()
}

// keep track of lastTimestamp and lastId so we can
// use it for the next request to do paging
def lastTimestamp
def lastId

if (filter != null) {

    def query = filter.accept(MapFilterVisitor.INSTANCE, null)
    //Need to handle the __UID__ and __NAME__ in queries - this map has entries for each objectType,
    //and is used to translate fields that might exist in the query object from the ICF identifier
    //back to the real property name.
    def fieldMap = [
             "__ACCOUNT__" : [
                    "__UID__" : "customerNumber",
                    "__NAME__": "customerName"
            ],
            "__COMPTE__"   : [
                    "__UID__" : "idcompte",
                    "__NAME__": "login"
            ]
    ]

    def whereTemplates = [
            CONTAINS          : '$left ${not ? "NOT " : ""}LIKE ?',
            ENDSWITH          : '$left ${not ? "NOT " : ""}LIKE ?',
            STARTSWITH        : '$left ${not ? "NOT " : ""}LIKE ?',
            EQUALS            : '${not ? "NOT " : ""} $left <=> ?',
            GREATERTHAN       : '$left ${not ? "<=" : ">"} ?',
            GREATERTHANOREQUAL: '$left ${not ? "<" : ">="} ?',
            LESSTHAN          : '$left ${not ? ">=" : "<"} ?',
            LESSTHANOREQUAL   : '$left ${not ? ">" : "<="} ?'
    ];

    // this closure function recurses through the (potentially complex) query object in order to build an equivalent SQL 'where' expression
    def queryParser
    queryParser = { queryObj ->

        if (queryObj.operation == "OR" || queryObj.operation == "AND") {
            return "(" + queryParser(queryObj.right) + " " + queryObj.operation + " " + queryParser(queryObj.left) + ")";
        } else {

            if (fieldMap[objectClass.objectClassValue] && fieldMap[objectClass.objectClassValue][queryObj.get("left")]) {
                queryObj.put("left", fieldMap[objectClass.objectClassValue][queryObj.get("left")]);
            }

            def engine = new groovy.text.SimpleTemplateEngine()
            def wt = whereTemplates.get(queryObj.get("operation"))
            def binding = [left: queryObj.get("left"), not: queryObj.get("not")]
            def template = engine.createTemplate(wt).make(binding)

            if (queryObj.get("operation") == "CONTAINS") {
                whereParams.push("%" + queryObj.get("right") + "%")
            } else if (queryObj.get("operation") == "ENDSWITH") {
                whereParams.push("%" + queryObj.get("right"))
            } else if (queryObj.get("operation") == "STARTSWITH") {
                whereParams.push(queryObj.get("right") + "%")
            } else {
                whereParams.push(queryObj.get("right"))
            }
            return template.toString()
        }
    }

    where = where + " AND "+ queryParser(query)
    log.ok("[referentielScriptedSQL] Search WHERE clause is: " + where)
}
def resultCount = 0
switch (objectClass) {
   case ObjectClass.ACCOUNT:
        def dataCollector = [ uid: "", comptes: [] ]

        def handleCollectedData = {
            if (dataCollector.uid != "") {
                handler {
                    uid dataCollector.customerNumber as String
                    id dataCollector.customerNumber
                    attribute 'customerNumber', dataCollector.customerNumber
                    attribute 'customerName', dataCollector.customerName
                    attribute 'contactFirstName', dataCollector.contactFirstName
                    attribute 'contactLastName', dataCollector.contactLastName
                    attributes AttributeBuilder.build('comptes', dataCollector.comptes)
                }

            }
        }

        def statement = """
            SELECT
            u.customerNumber,
            u.customerName,
            u.contactFirstName,
            u.contactLastName,
            c.login,
            c.idcompte
            FROM
            customers u
            LEFT OUTER JOIN
            compte c
            ON c.customerid = u.customerNumber
            ${where}
            ${orderBy}
            ${limit}
        """

        sql.eachRow(statement, whereParams, { row ->
            if (dataCollector.uid != row.uid) {
                // new user row, process what we've collected

                handleCollectedData();

                dataCollector = [
                        customerNumber : row.customerNumber as String,
                        customerName : row.customerName,
                        contactFirstName: row.contactFirstName,
                        contactLastName: row.contactLastName,
                        comptes : [ ]
                ]
            }

            if (row.login) {
                dataCollector.comptes.add([
                          login: row.login,
                        idcompte: row.idcompte
                ])
            }

            lastId = row.customerNumber
            resultCount++
        });

        handleCollectedData();

        break





    default:
        throw new UnsupportedOperationException(operation.name() + " operation of type:" +
                objectClass.objectClassValue + " is not supported.")
}

// If paging is not wanted just return the default SearchResult object
if (orderBy.toString().isEmpty() || limit.toString().isEmpty() || resultCount < options.pageSize) {
    return new SearchResult();
}

return new SearchResult(lastTimestamp.toString() + "," + lastId.toString(), -1);
