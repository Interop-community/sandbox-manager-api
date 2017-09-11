/*
 * #%L
 *
 * %%
 * Copyright (C) 2014 - 2015 Healthcare Services Platform Consortium
 * %%
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
 * #L%
 */

package org.hspconsortium.sandboxmanagerapi.controllers;

import org.hspconsortium.sandboxmanagerapi.model.SystemRole;
import org.hspconsortium.sandboxmanagerapi.model.TermsOfUse;
import org.hspconsortium.sandboxmanagerapi.model.User;
import org.hspconsortium.sandboxmanagerapi.services.OAuthService;
import org.hspconsortium.sandboxmanagerapi.services.TermsOfUseService;
import org.hspconsortium.sandboxmanagerapi.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.util.Date;

@RestController
@RequestMapping("/api/termsofuse")
public class TermsOfUseController extends AbstractController  {
    private static Logger LOGGER = LoggerFactory.getLogger(TermsOfUseController.class.getName());

    private final TermsOfUseService termsOfUseService;
    private final UserService userService;

    @Inject
    public TermsOfUseController(final TermsOfUseService termsOfUseService, final OAuthService oAuthService,
                                final UserService userService) {
        super(oAuthService);
        this.termsOfUseService = termsOfUseService;
        this.userService = userService;
    }

    @CrossOrigin(origins = "*")
    @RequestMapping(method = RequestMethod.GET, produces ="application/json")
    public ResponseEntity<TermsOfUse> getLatestTermsOfUse() {
        TermsOfUse mostRecent = termsOfUseService.mostRecent();
        if (mostRecent != null) {
            return new ResponseEntity<>(mostRecent, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @RequestMapping(method = RequestMethod.POST, produces ="application/json")
    public TermsOfUse createTermsOfUse(HttpServletRequest request, @RequestBody final TermsOfUse termsOfUse) {
        User user = userService.findBySbmUserId(getSystemUserId(request));
        checkUserSystemRole(user, SystemRole.ADMIN);

        termsOfUse.setCreatedTimestamp(new Timestamp(new Date().getTime()));
        return termsOfUseService.save(termsOfUse);
    }
}