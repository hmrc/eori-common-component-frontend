# How to add a new service
 
The following are the steps required to update the EORI Comment Component to add support for getting access to new service.

# Before you start
You will need the following information
- a "code" for the new service.  This can be any short string.  It needs to be unique and it forms part of the url to access the new service.  An abbreviation of the service name in lower-case is usually a good starting point.
- the enrolment key for the new service.  Details for creating a new service enrolment key can be found [here](https://github.com/hmrc/service-enrolment-config)
- the call-back url.  This is the url EORI Common Component will re-direct the user to once they have access to the new service.

# Add definition to the Service model

In the file `app/uk/gov/hmrc/eoricommoncomponent/frontend/models/Service.scala` add a definition for the new service. For example

```
  case object DemoService extends Service {
    override val code: String         = "demo"
    override val enrolmentKey: String = "HMRC-DEMO-ORG"
  }
```

Add the new service to the collection of supported services by updating the following
```
  private val supportedServices = Set[Service](ATaR, DemoService)
```

# Add call-back Url
In the file `conf/application.conf` add an entry for the url ECC should use to return the user to the service once enrolment is complete. For example
```
  service-return {
    atar="http://localhost:9582/advance-tariff-application"
    demo="/demo-service/start"
  }
```

If the call-back url is to a "local" server (e.g. `http://localhost:1234//demo-service/start`) then the configuration in `app-config-base` will need to have an entry specifying a relative url.
See [app-config-base]("https://github.com/hmrc/app-config-base/blob/master/eori-common-component-frontend.conf") for an example.


# Add "friendly" names to the messages files

In the file `conf/messages-ecc.en` (and `conf/messages-ecc.cy` for Welsh translations) add two new entries like this 
```
cds.service.short.name.demo=Demo
cds.service.friendly.name.demo=Demo Service
```

where `"demo"` is the code of the new service defined in the previous step.

# Test

[Run locally](README.md#running-locally) and navigate to the start url for the new service

Subscribe - `http://localhost:6750/customs-enrolment-services/demo/subscribe`

replacing `/demo/` in the above url with the code defined for the new service.