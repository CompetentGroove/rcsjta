/*
 * Copyright 2013, France Telecom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gsma.joyn.session;

import java.lang.String;

/**
 * IMS session errors constants and utilities
 */
public class ImsSessionBasedServiceError extends ImsServiceError {
	
	static final long serialVersionUID = 1L;

    /**
     * Session initiation failure
     */
    public static final int SESSION_INITIATION_FAILED = 101;

    /**
     * Session initiation declined by peer
     */
    public static final int SESSION_INITIATION_DECLINED = 102;

    /**
     * Session initiation cancelled
     */
    public static final int SESSION_INITIATION_CANCELLED = 103;

    /**
     * Chat error
     */
    protected static final int CHAT_ERROR_CODES = 0;

    /**
     * File transfer error
     */
    protected static final int FT_ERROR_CODES = 0;

    /**
     * Rich call error
     */
    protected static final int RICHCALL_ERROR_CODES = 0;

    /**
     * SIP error
     */
    protected static final int SIP_ERROR_CODES = 0;

    /**
     * Creates an IMS session error using an IMS service error object
     *
     * @param error The IMS Service error
     */
    public ImsSessionBasedServiceError(ImsServiceError error) {
        super(0);
    }

    /**
     * Creates a IMS session error using an IMS service code error
     *
     * @param code The IMS error code
     */
    public ImsSessionBasedServiceError(int code) {
        super(0);
    }

    /**
     * Creates a IMS session error using an IMS service code error and a message
     *
     * @param code IMS error code
     * @param message Error message
     */
    public ImsSessionBasedServiceError(int code, String message) {
        super(0);
    }

}
