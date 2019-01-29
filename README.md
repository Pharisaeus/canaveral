```
                                                   ,:
                                                 ,' |
                                                /   :
                                             =='   /==
                                              / O / .
                                             /   /.
                                          __/   /
                                          )'-. /
                                          ./  :\
                                           /.' '
                                         '/'
                                         +
                                        '
                                      `.
                                  .-"-
                                 (    |
                              . .-'  '.
                             ( (.   )8:
                         .'    / (_  )
                          _. :(.   )8P  `
                      .  (  `-' (  `.   .
                       .  :  (   .a8a)
                      /_`( "a `a. )"'
                  (  (/  .  ' )=='
                 (   (    )  .8"   +   
                   (`'8a.( _(   (      
                ..-. `8P    ) `  )  +  
              -'   (      -ab:  )      
            '    _  `    (8P"Ya        
          _(    (    )b  -`.  ) +      
         ( 8)  ( _.aP" _a   \( \   *   
       +  )/    (8P   (88    )  )
          (a:f   "     `"       `
   ______      ___      .__   __.      ___      ____    ____  _______ .______           ___       __
  /      |    /   \     |  \ |  |     /   \     \   \  /   / |   ____||   _  \         /   \     |  |
 |  ,----'   /  ^  \    |   \|  |    /  ^  \     \   \/   /  |  |__   |  |_)  |       /  ^  \    |  |
 |  |       /  /_\  \   |  . `  |   /  /_\  \     \      /   |   __|  |      /       /  /_\  \   |  |
 |  `----. /  _____  \  |  |\   |  /  _____  \     \    /    |  |____ |  |\  \----. /  _____  \  |  `----.
  \______|/__/     \__\ |__| \__| /__/     \__\     \__/     |_______|| _| `._____|/__/     \__\ |_______|
                                                                                Integration Tests Launcher
```

[![CircleCI](https://img.shields.io/circleci/project/github/codewise-oss/canaveral.svg)](https://circleci.com/gh/codewise-oss/canaveral)
[![CircleCI](https://img.shields.io/github/release/codewise-oss/canaveral.svg)](https://github.com/codewise-oss/canaveral/releases)

Canaveral was created to aid developing and maintaining integration tests in codewise.com. This is new version
that has less dependencies and is more flexible. 

Currently junit 4.x, junit 5.x and test-ng is supported, but adding support for other runners is quite trivial.

Canaveral's biggest advantage is a big set of mocks that are easily configurable by custom dsl and such configuration
can be copied from project to project while implementation details are hidden. The faster you can set your integration
test the faster you can catch and remove bugs.

### Test Configuration
```
@ConfigureRunnerWith(configuration = ExampleRunnerConfiguration.class)
public abstract class ITBaseTest {
    ....
}
```

### Runner Configuration file
Some mocks have additional ref name, but this is optional as runner will provide name from mockProvider class.
```java
public class ExampleRunnerConfiguration implements RunnerConfigurationProvider {

    @Override
    public RunnerConfiguration configure() {
        return RunnerConfiguration.builder()
                .withApplicationProvider(new SpringBootApplicationProvider(MySpringBootApp.class, ...))
                .withTestConfigurationProvider(SpringTestContextProvider.setUp()...build())
                .withSystemProperties("spring.profiles.active", "test")
                .registerRandomPortUnder("server.port")
                .withSystemProperty("default.service.property", "ok")
                .withSystemProperties("aws.correlator.timeoutMillis", "5000")
                .withMocks(RunnerConfiguration.mocksBuilder()
                        .provideMock("http", HttpNoDepsMockProvider.newConfig()
                                 .registerEndpointUnder("dummy.endpoint.property")
                                 .registerPortUnder("dummy.port.property")
                                 .withRules(rules -> rules
                                         .whenCalledWith(Method.GET, "/path-to-resource")
                                         .accepting(Mime.JSON)
                                         .withHeader("b", "3")
                                         .withQueryParam("a", "1")
                                         .thenRespondWith(Body.asJsonFrom("{\"name\": \"bob\"}"))
                                 )
                        )
                )
                .build();
    }
}
```

for more details see ```pl.codewise.samples.spring.it.BaseIT``` in canaveral-samples module
