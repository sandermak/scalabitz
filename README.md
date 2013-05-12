
![Scalabitz logo](https://raw.github.com/sandermak/scalabitz/master/public/images/scalabitz_logo.png)


[Scalabitz.com](http://scalabitz.com) is a site that surfaces interesting Scala content around the web using Bit.ly's API. Read [this blogpost](http://branchandbound.net/?) for a more in-depth description of the project.

### Build
Since the app is a standard Play app, it is built using SBT. There is one dependency outside of core Play to the play2-reactivemongo extension, which in turns has a transitive dependency on the [ReactiveMongo](http://reactivemongo.org) driver. 

### Deployment
Currently, this app is deployed on Heroku. However, it is just a plain Play application and can be deployed anywhere. Note that the ```application.conf``` takes several configuration values from environment variables. To run the app, create a script that provides the correct values for these parameters. An example:

```sh
#!/bin/sh
export MONGOHQ_URL="mongodb://localhost:27017/scalabitz"
export DBNAME=scalabitz
export APP_SECRET='generated secret by Play'
export BITLY_TOKEN=<your bitly secret token>
export USERNAME=<username for the admin section>
export PASSWORD=<password for the admin section>
export TIMEOUT=3600
play clean-all run 
```

As can be deduced from the MONGOHQ_URL variable name, the site currently uses a hosted Mongo instance from MongoHQ. However, any valid MongoDB connection string may be provided. Like in the example above, on localhost.