# How to add a new service
The following steps are required to add support for getting access to new service using EORI Comment Component (ECC).

# Configuration keys
The following information is required to enable getting access to a new service using ECC

| Key                     | Description             | 
| -------------           | ----------------------- | 
| `name`                  | A unique name which forms part of the url to access the new service.  An abbreviation of the service name in lower-case is usually good. | 
| `enrolment`             | The enrolment key for the new service.  Details for creating a new service enrolment key can be found [here](https://github.com/hmrc/service-enrolment-config). | 
| `callBack`              | This is the url ECC will re-direct the user to once they have access to the new service. | 
| `friendlyName`          | This is the "long" name of the service that is used on confirmation pages and emails. | 
| `friendlyNameWelsh`     | (Optional) Welsh translation of the long name.| 
| `shortName`             | This is the abbreviation of the service. | 

# Add new configuration for each environment
Add definitions for the new service to the `eori-common-component-frontend.yaml` file for each environment.

To add a new "Example" service:
```
services-config.0.name: "example"
services-config.0.enrolment: "HMRC-EXAMPLE-ORG"
services-config.0.shortName: "EXAMPLE"
services-config.0.callBack: "/example-service/start"
services-config.0.friendlyName: "Example Service Name"
services-config.0.friendlyNameWelsh: "Optional Welsh Service Name"
```

If already some service/services exists in the config please use index incremented by 1.

In that case, another service will require `services-config.1.name: "AnotherExample"` etc.

*Note*:  This definition needs to be added to each environment separately.  For reference - 
- [Development](https://github.com/hmrc/app-config-development/blob/master/eori-common-component-frontend.yaml)
- [QA](https://github.com/hmrc/app-config-qa/blob/master/eori-common-component-frontend.yaml)
- [Staging](https://github.com/hmrc/app-config-staging/blob/master/eori-common-component-frontend.yaml)
- [External Test](https://github.com/hmrc/app-config-externaltest/blob/master/eori-common-component-frontend.yaml)
- [Production](https://github.com/hmrc/app-config-production/blob/master/eori-common-component-frontend.yaml)

# Test
The url for the new "Example" service would be
`/customs-enrolment-services/example/subscribe`