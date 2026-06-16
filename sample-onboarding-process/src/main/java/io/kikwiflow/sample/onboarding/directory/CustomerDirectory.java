/*
 * Copyright 2026 Atoxfy and/or licensed to Atoxfy
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Atoxfy licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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

package io.kikwiflow.sample.onboarding.directory;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class CustomerDirectory {

    private final Map<String, Optional<Customer>> customersDirectory = new HashMap<>();
    public static final String APPROVED_CUSTOMER_TAX_ID = "1";
    public static final String FRAUD_CUSTOMER_TAX_ID = "2";

    public CustomerDirectory(){
        Customer approvedCustomer = new Customer(APPROVED_CUSTOMER_TAX_ID, "John Doe", LocalDate.of(1991, 1, 1), "123132");
        customersDirectory.put(approvedCustomer.taxId, Optional.of(approvedCustomer));

        Customer fraudCustomer = new Customer(FRAUD_CUSTOMER_TAX_ID, "Buzz Ald", LocalDate.of(2014, 5, 1), "123132");
        customersDirectory.put(fraudCustomer.taxId, Optional.of(fraudCustomer));
    }

    public Optional<Customer> findByTaxId(String taxId){
        Optional<Customer> c = customersDirectory.get(taxId);
        return c != null ? c : Optional.empty();
    }

    public record Customer(
            String taxId,
            String name,
            LocalDate birthDate,
            String zipCode){}
}
