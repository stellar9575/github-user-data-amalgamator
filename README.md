# Github User Data Amalgamator

## Overview
The **Github User Data Amalgamator** is a Spring Boot application that retrieves and combines information about a GitHub user. Given a GitHub username, the service returns:
* Basic user profile data
* A list of the user’s public repositories

## Dependencies
This project requires:
* Java 17+ (records are used for data modeling)
* Gradle 8+ (build and dependency management)
* Spring Boot 3+
  * spring-boot-starter-web (REST API)
  * spring-boot-starter-webflux (for WebClient HTTP calls)

You can inspect the exact versions in `build.gradle`.

## Rationale
This project uses industry standard libraries and dependencies. I choose to incorporate a caching mechanism to improve speed and reliability (in case github goes down), while also leaving the option to disable caching behavior. The choice of a LRU cache was for simplicity, but a LFU cache may yield better results with more time to code. I added the option to add an access token for increased rate calling to github. My choice of using gradle to build and run the project aligns with widespread use, though an option to use maven could be added. Full integrated tests and git flow build pipelines would be the next steps to building a more mature, production ready result.

## Installation
Clone the repository:
```
git clone https://github.com/stellar9575/github-user-data-amalgamator.git
cd github-user-data-amalgamator
```
## Running
Build and run the application:

`./gradlew bootRun`

You can then call the endpoint:

`curl http://localhost:8080/users/<yourUsername>`

Once requested, the service caches that user data for fast subsequent retrieval.
### Environment Variables
You can pass the following variables at runtime using Gradle project properties:

* GitHub API access token (optional)
  * If omitted, unauthenticated GitHub requests are subject to lower rate limits. 
* LRU cache size (optional, default - 1000)
  * If no caching behavior is desired, set to 0. 

`./gradlew bootRun --args='--api.access.token=yourToken --lru.cache.size=yourCacheSize'`

### Allowed inputs
Current github policy requires that the username must be 39 characters or fewer and contain only the following characters:
* Letters: a–z, A–Z
* Numbers: 0–9
* Dash: -
### Expected outputs
An example of a successful response:
```
{
    "user_name": "torvalds",
    "display_name": "Linus Torvalds",
    "avatar": "https://avatars.githubusercontent.com/u/1024025?v=4",
    "geo_location": "Portland, OR",
    "email": null,
    "url": "https://api.github.com/users/torvalds",
    "created_at": "2011-09-03T15:26:22Z",
    "repos": [
        {
            "name": "1590A",
            "url": "https://api.github.com/repos/torvalds/1590A"
        },
        ...
    ]
}
```
## Testing
You can run the test suite via:

`./gradlew test`
