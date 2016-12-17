angular.module("sandManApp.branding", [], ["$provide", function($provide) {

    $provide.value("brandedText", {
            hspc: {
                mainImage: "static/branding/hspc/images/hspc-sndbx-logo.png",
                mainImage2x: "static/branding/hspc/images/hspc-sndbx-logo@2x.png 2x",
                whiteImage: "static/branding/hspc/images/hspc-sndbx-logo-wh.png",
                whiteImage2x: "static/branding/hspc/images/hspc-sndbx-logo-wh.png 2x",
                mainTitle: "The Healthcare Innovation Ecosystem",
                sandboxText: "",
                moreLinks: true,
                dashboardTitle: "Dashboard",
                showEmptyInviteList: true,
                copyright: "© 2016 by Healthcare Services Platform Consortium",
                showCert: true,
                loginDoc: "",
                defaultSchemaVersion : "2",
                sandboxSchemaVersions : [
                    {version: "1", name: "FHIR DSTU 2"},
                    {version: "2", name: "FHIR STU 3"}
                ],
                sandboxDescription: {
                    title: "What is a sandbox?",
                    description: "A sandbox is your very own instance of an HSPC Platform* combined with tools and utilities to help you build and test your medical apps.",
                    bottomNote: "*An HSPC Platform is a standardized way to interact with a medical system such as an EHR, Hospital, Clinic, HIE, PHR, Lab, Insurer, etc.",
                    checkList: [
                        "Create apps for practitioners that launch within an EHR, smart phone, tablet, or web browser",
                        "Create apps for patients and their related persons that launch from a smart phone, tablet, web browser, or personal computer",
                        "Create backend services that interact directly with HSPC Platforms*",
                        "Verify your app follows the SMART security and launch context standards",
                        "Run your app against your very own FHIR server",
                        "Test your apps by creating various launch scenarios",
                        "Create practitioners, patients, and clinical data",
                        "Verify that your app is HSPC compliant"
                    ]
                }
            }
    });
}]);