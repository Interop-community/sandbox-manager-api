'use strict';

angular.module('sandManApp.services', [])
    .factory('oauth2', function($rootScope, $location) {

        var authorizing = false;

        return {
            authorizing: function(){
                return authorizing;
            },
            authorize: function(s){
                // window.location.origin does not exist in some non-webkit browsers
                if (!window.location.origin) {
                    window.location.origin = window.location.protocol + "//"
                        + window.location.hostname
                        + (window.location.port ? ':' + window.location.port: '');
                }

                var thisUri = window.location.origin + window.location.pathname;
                var thisUrl = thisUri.replace(/\/+$/, "/");

                var client = {
                    "client_id": "sand_man",
                    "redirect_uri": thisUrl,
                    "scope":  "smart/orchestrate_launch user/*.* profile openid"
                };
                authorizing = true;
                FHIR.oauth2.authorize({
                    client: client,
                    server: s.serviceUrl,
                    from: $location.url()
                }, function (err) {
                    authorizing = false;
                    $rootScope.$emit('error', err);
//                    $rootScope.$emit('set-loading');
//                    $rootScope.$emit('clear-client');
//                    var loc = "/ui/select-patient";
//                    if ($location.url() !== loc) {
//                        $location.url(loc);
//                    }
//                    $rootScope.$digest();
                });
            }
        };

    }).factory('fhirApiServices', function (oauth2, appsSettings, patientDetails, $rootScope, $location) {

        /**
         *
         *      FHIR SERVICE API CALLS
         *
         **/

        var fhirClient;

        function getQueryParams(url) {
            var index = url.lastIndexOf('?');
            if (index > -1){
                url = url.substring(index+1);
            }
            var urlParams;
            var match,
                pl     = /\+/g,  // Regex for replacing addition symbol with a space
                search = /([^&=]+)=?([^&]*)/g,
                decode = function (s) { return decodeURIComponent(s.replace(pl, " ")); },
                query  = url;

            urlParams = {};
            while (match = search.exec(query))
                urlParams[decode(match[1])] = decode(match[2]);
            return urlParams;
        }

        return {
            clearClient: function(){
                fhirClient = null;
                sessionStorage.clear();
            },
            fhirClient: function(){
                return fhirClient;
            },
            initClient: function(){
                var params = getQueryParams($location.url());
                if (params.code){
                    delete sessionStorage.tokenResponse;
                    FHIR.oauth2.ready(params, function(newSmart){
                        if (newSmart && newSmart.state && newSmart.state.from !== undefined){
                            $location.url(newSmart.state.from);
                            fhirClient = newSmart;
                            window.fhirClient = fhirClient;
                            $rootScope.$emit('signed-in');
                            $rootScope.$digest();
                        }
                    });
                } else {
                    appsSettings.getSettings().then(function(settings){
                        oauth2.authorize(settings);
                    });
                }
            },
            hasNext: function(lastSearch) {
                var hasLink = false;
                if (lastSearch  === undefined) {
                    return false;
                } else {
                    lastSearch.data.link.forEach(function(link) {
                        if (link.relation == "next") {
                            hasLink = true;
                        }
                    });
                }
                return hasLink;
            },
            getNextOrPrevPage: function(direction, lastSearch) {
                var deferred = $.Deferred();
                $.when(fhirClient.api[direction]({bundle: lastSearch.data}))
                    .done(function(pageResult){
                        var resources = [];
                        if (pageResult.data.entry) {
                            pageResult.data.entry.forEach(function(entry){
                                resources.push(entry.resource);
                            });
                        }
                        deferred.resolve(resources, pageResult);
                    });
                return deferred;
            },
            queryResourceInstances: function(resource, searchValue, tokens, sort) {
                var deferred = $.Deferred();

                var searchParams = {type: resource, count: 50};
                searchParams.query = {};
                if (searchValue !== undefined) {
                    searchParams.query = searchValue;
                }
                if (typeof sort !== 'undefined' ) {
                    searchParams.query['$sort'] = sort;
                }
                if (typeof sort !== 'undefined' ) {
                    searchParams.query['name'] = tokens;
                }

                $.when(fhirClient.api.search(searchParams))
                    .done(function(resourceSearchResult){
                        var resourceResults = [];
                        if (resourceSearchResult.data.entry) {
                            resourceSearchResult.data.entry.forEach(function(entry){
                                entry.resource.fullUrl = entry.fullUrl;
                                resourceResults.push(entry.resource);
                            });
                        }
                        deferred.resolve(resourceResults, resourceSearchResult);
                    });
                return deferred;
            },
            registerContext: function(app, params){
                var deferred = $.Deferred();

                var req = fhirClient.authenticated({
                    url: fhirClient.server.serviceUrl + '/_services/smart/Launch',
                    type: 'POST',
                    contentType: "application/json",
                    data: JSON.stringify({
                        client_id: app.client_id,
                        parameters:  params
                    })
                });

                $.ajax(req)
                    .done(deferred.resolve)
                    .fail(deferred.reject);

                return deferred;
            }
        }
    }).factory('launchScenarios', function() {

        var scenarioBuilder = {
            description: '',
            persona: '',
            patient: '',
            app: ''
        };

        var selectedScenario;

        var recentLaunchScenarioList = [];
        recentLaunchScenarioList.push({
            desc: "Bilirubin",
            persona: {name: "Kurtis Giles MD",
                id: "COREPRACTITIONER1",
                fullUrl: "http://localhost:8080/hspc-reference-api/data/Practitioner/COREPRACTITIONER1",
                      resource: "Practitioner"},
            patient: {name: "Bili Baby",
                id: "BILIBABY",
                resource: "Patient"},
            app: {client_name: "Bilirubin"}
        });

        var fullLaunchScenarioList = [];
        fullLaunchScenarioList.push({
            desc: "Bilirubin",
            persona: {name: "Kurtis Giles MD",
                id: "COREPRACTITIONER1",
                fullUrl: "http://localhost:8080/hspc-reference-api/data/Practitioner/COREPRACTITIONER1",
                resource: "Practitioner"},
            patient: {name: "Bili Baby",
                id: "BILIBABY",
                resource: "Patient"},
            app: {client_name: "Bilirubin"}
        });
        fullLaunchScenarioList.push({
            desc: "Appointment Viewer wo/ Patient",
            persona: {name: "Kurtis Giles MD",
                id: "COREPRACTITIONER1",
                fullUrl: "http://localhost:8080/hspc-reference-api/data/Practitioner/COREPRACTITIONER1",
                resource: "Practitioner"},
            patient: {name: "None",
                resource: "Patient"},
            app: {client_name: "Appointment Viewer"}
        });


        return {
            clearBuilder: function() {
                scenarioBuilder = {
                    description: '',
                    persona: '',
                    patient: '',
                    app: ''
                };
            },
            getBuilder: function() {
                return scenarioBuilder;
            },
            setDescription: function(desc) {
                scenarioBuilder.description = desc;
            },
            setPersona: function(persona) {
                scenarioBuilder.persona = persona;
            },
            setPatient: function(patient) {
                scenarioBuilder.patient = patient;
            },
            setApp: function(app) {
                scenarioBuilder.app = app;
            },
            setSelectedScenario: function(scenario) {
                selectedScenario = scenario;
            },
            getSelectedScenario: function() {
                return selectedScenario;
            },
            getFullLaunchScenarioList: function() {
                return fullLaunchScenarioList;
            },
            addFullLaunchScenarioList: function(launchScenario) {
                fullLaunchScenarioList.push(launchScenario);
            },
            getRecentLaunchScenarioList: function() {
                return recentLaunchScenarioList;
            },
            addRecentLaunchScenarioList: function(launchScenario) {
                recentLaunchScenarioList.push(launchScenario);
            }

        }

    }).factory('userServices', function($rootScope, fhirApiServices, patientDetails, appsSettings) {
        var persona = {};
        var oauthUser = {};

        return {
            persona: function(){
                return persona;
            },
            oauthUser: function(){
                return oauthUser;
            },
            updateProfile: function(selectedUser){
                appsSettings.getSettings().then(function(settings){

                    $.ajax({
                        url: settings.profile_update_uri,
                        type: 'POST',
                        data: JSON.stringify({
                            user_id: oauthUser.sub,
                            profile_url: selectedUser.fullUrl
                        }),
                        contentType: "application/json"
                    }).done(function(result){
                            //TODO check result value for 200 or 201
//                        persona = selectedUser;
//                        $rootScope.$emit('profile-change');
                            $rootScope.$digest();
                        }).fail(function(){
                        });
                });
            },
            getFhirProfileUser: function() {
                var deferred = $.Deferred();
                if (fhirApiServices.fhirClient().userId === null ||
                    typeof fhirApiServices.fhirClient().userId === "undefined"){
                    deferred.resolve(null);
                    return deferred;
                }
                var historyIndex = fhirApiServices.fhirClient().userId.lastIndexOf("/_history");
                var userUrl = fhirApiServices.fhirClient().userId;
                if (historyIndex > -1 ){
                    userUrl = fhirApiServices.fhirClient().userId.substring(0, historyIndex);
                }
                var userIdSections = userUrl.split("/");

                $.when(fhirApiServices.fhirClient().api.read({type: userIdSections[userIdSections.length-2], id: userIdSections[userIdSections.length-1]}))
                    .done(function(userResult){

                        var user = {name:""};
                        user.name = patientDetails.name(userResult.data);
                        user.id  = patientDetails.id(userResult.data);
                        persona = user;
                        persona.fullUrl = userResult.config.url;
                        deferred.resolve(user);
                    });
                return deferred;
            },
            getOAuthUser: function() {
                var deferred = $.Deferred();
                var userInfoEndpoint = fhirApiServices.fhirClient().state.provider.oauth2.authorize_uri.replace("authorize", "userinfo");
                $.ajax({
                    url: userInfoEndpoint,
                    type: 'GET',
                    contentType: "application/json",
                    beforeSend : function( xhr ) {
                        xhr.setRequestHeader( 'Authorization', 'BEARER ' + fhirApiServices.fhirClient().server.auth.token );
                    }
                }).done(function(result){
                        oauthUser = result;
                        deferred.resolve(result);
                    }).fail(function(){
                    });
                return deferred;
            }
        };
    }).factory('patientDetails', function() {
        return {
            id: function(p){
                return p.id;
            },
            name: function(p){
                if (p.resourceType === "Patient") {
                    var name = p && p.name && p.name[0];
                    if (!name) return null;

                    return name.given.join(" ") + " " + name.family.join(" ");
                } else {
                    var name = p && p.name;
                    if (!name) return null;

                    var practitioner =  name.given.join(" ") + " " + name.family.join(" ");
                    if (name.suffix) {
                        practitioner = practitioner + " " + name.suffix.join(", ");
                    }
                    return practitioner;
                }
            }
        };
    }).factory('customFhirApp', function() {

        var app = localStorage.customFhirApp ?
            JSON.parse(localStorage.customFhirApp) : {id: "", url: ""};

        return {
            get: function(){return app;},
            set: function(app){
                localStorage.customFhirApp = JSON.stringify(app);
            }
        };

    }).factory('launchApp', function($rootScope, fhirApiServices, random) {

        return {
            /* Hack to get around the window popup behavior in modern web browsers
             (The window.open needs to be synchronous with the click even to
             avoid triggering  popup blockers. */

            launch: function(app, patientContext) {
                var key = random(32);
                window.localStorage[key] = "requested-launch";
                var appWindow = window.open('launch.html?'+key, '_blank');

                var params = {};
                if (patientContext !== undefined) {
                    params = {patient: patientContext.id}
                }

                fhirApiServices
                    .registerContext(app, params)
                    .done(function(c){
                        console.log(fhirApiServices.fhirClient());
                        window.localStorage[key] = JSON.stringify({
                            app: app,
                            iss: fhirApiServices.fhirClient().server.serviceUrl,
                            context: c
                        });
                    }).fail( function(err){
                        console.log("Could not register launch context: ", err);
                        appWindow.close();
                        //                    $rootScope.$emit('reconnect-request');
                        $rootScope.$emit('error', 'Could not register launch context (see console)');
                        $rootScope.$digest();
                    });
            }
        }

    }).factory('random', function() {
        var chars = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
        return function randomString(length) {
            var result = '';
            for (var i = length; i > 0; --i) {
                result += chars[Math.round(Math.random() * (chars.length - 1))];
            }
            return result;
        }
    }).factory('apps', ['$http',function($http)  {
        return {
            getPatientApps: $http.get('static/js/config/patient-apps.json'),
            getPractitionerPatientApps: $http.get('static/js/config/practitioner-patient-apps.json'),
            getPractitionerApps: $http.get('static/js/config/practitioner-apps.json')
        };

    }]).factory('appsSettings', ['$http',function($http)  {

    var settings;

    return {
        loadSettings: function(){
            var deferred = $.Deferred();
            $http.get('static/js/config/sandbox-manager.json').success(function(result){
                    settings = result;
                    deferred.resolve(result);
                });
            return deferred;
        },
        getSettings: function(){
            var deferred = $.Deferred();
            if (settings !== undefined) {
                deferred.resolve(settings);
            } else {
                this.loadSettings().then(function(result){
                    deferred.resolve(result);
                });
            }
            return deferred;
        }
    };

}]);
