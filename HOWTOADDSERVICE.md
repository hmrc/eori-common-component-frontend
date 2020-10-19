# How to add a new service
This article details the configuration needed to add support for enroling to a new HMRC service to the EORI Comment Component (ECC).

# Configuration values required
The following values are required to add the configuration for a new service.

| Key                     | Description             | 
| -------------           | ----------------------- | 
| `enrolment`             | The enrolment key for the new service.  Details for creating a new service enrolment key can be found [here](https://github.com/hmrc/service-enrolment-config). | 
| `shortName`             | This is the abbreviation of the service. | 
| `callBack`              | This is the url ECC will re-direct the user to once they have completed an enrolment request. | 
| `friendlyName`          | This is the "long" name of the service that is used on confirmation pages and emails. | 
| `friendlyNameWelsh`     | (Optional) Welsh translation of the long name.| 

# Add configuration for each environment
Add definitions for the new service to the `eori-common-component-frontend.yaml` configuration file for *each* environment.

## Step 1
Decide on a unique key for the new service. This unique key forms part of the url to access the new service - usually an abbreviation of the service name in lower-case.


## Step 2
Add to the list of services by appending a comma and the unique key to `services-config.list`, for example.
```
services-config.list: "atar,example"
```

## Step 3
Add the following entries to the configuration file replacing `example` with the unique key.
```
services-config.example.enrolment: "[ENROLMENT_KEY_HERE]"
services-config.example.shortName: "[SHORT_NAME_HERE]"
services-config.example.callBack: "[URL_HERE]"
services-config.example.friendlyName: "[FRIENDLY_NAME_HERE]"
services-config.example.friendlyNameWelsh: "[(OPTIONAL)WELSH_NAME_HERE]"
```

For example
```
services-config.example.enrolment: "HMRC-NEW-ORG"
services-config.example.shortName: "NEWS"
services-config.example.callBack: "/new-service/start"
services-config.example.friendlyName: "New_Service"
services-config.example.friendlyNameWelsh: "Optional_Welsh_Service_Name"
```

## Notes
1. The configuration file cannot have any spaces in the values - replace spaces with underscore in the friendly names (see examples above).  The underscores will be removed when the names are used by ECC.

2. The configuration must be updated for each environment where the service is required.  For reference - 
- [Development](https://github.com/hmrc/app-config-development/blob/master/eori-common-component-frontend.yaml)
- [QA](https://github.com/hmrc/app-config-qa/blob/master/eori-common-component-frontend.yaml)
- [Staging](https://github.com/hmrc/app-config-staging/blob/master/eori-common-component-frontend.yaml)
- [External Test](https://github.com/hmrc/app-config-externaltest/blob/master/eori-common-component-frontend.yaml)
- [Production](https://github.com/hmrc/app-config-production/blob/master/eori-common-component-frontend.yaml)

# Test
The url for the new "Example" service would be
`/customs-enrolment-services/example/subscribe`