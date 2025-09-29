<!-- <nav> -->
- [Akka](../../index.html)
- [Getting Started](../index.html)
- [Build an AI multi-agent planner](index.html)
- [List by user](list.html)

<!-- </nav> -->

# List by user

|  | **New to Akka? Start here:**

Use the [Author your first agentic service](../author-your-first-service.html) guide to get a simple agentic service running locally and interact with it. |

## <a href="about:blank#_overview"></a> Overview

We can retrieve the result of an individual user query directly from the workflow, using the session id that corresponds to the workflow id. It would be nice to list the historical result by user id.

In this part of the guide you will:

- Add a view to be able to query all activity suggestions for a user
- Add a method in the endpoint to use the view

## <a href="about:blank#_prerequisites"></a> Prerequisites

- Java 21, we recommend [Eclipse Adoptium](https://adoptium.net/marketplace/)
- [Apache Maven](https://maven.apache.org/install.html) version 3.9 or later
- <a href="https://curl.se/download.html">`curl` command-line tool</a>
- [OpenAI API key](https://platform.openai.com/api-keys)

## <a href="about:blank#_add_a_view"></a> Add a view

Add a new file `ActivityView.java` to `src/main/java/com/example/application/`

ActivityView.java
```java
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

import java.util.List;

@Component(id = "activity-view")
public class ActivityView extends View {
  public record ActivityEntries(List<ActivityEntry> entries) {}

  public record ActivityEntry(String userId, String userQuestion, String finalAnswer) {}

  @Query("SELECT * AS entries FROM activities WHERE userId = :userId") // (1)
  public QueryEffect<ActivityEntries> getActivities(String userId) {
    return queryResult();
  }

  @Consume.FromWorkflow(AgentTeamWorkflow.class) // (2)
  public static class Updater extends TableUpdater<ActivityEntry> {
    public Effect<ActivityEntry> onStateChange(AgentTeamWorkflow.State state) {
      return effects()
          .updateRow(new ActivityEntry(state.userId(), state.userQuery(), state.finalAnswer()));
    }

    @DeleteHandler
    public Effect<ActivityEntry> onDelete() {
      return effects().deleteRow();
    }
  }

}
```

| **1** | The query selects all rows for a given user id. |
| **2** | The view is updated from the state changes of the workflow. |

## <a href="about:blank#_expose_in_the_endpoint"></a> Expose in the endpoint

Add a new method that asks the view for a given user id.

```java
public record ActivitiesList(List<Suggestion> suggestions) {
    static ActivitiesList fromView(ActivityView.ActivityEntries entries) {
      return new ActivitiesList(entries.entries().stream().map(Suggestion::fromView).toList());
    }
  }

  public record Suggestion(String userQuestion, String answer) {
    static Suggestion fromView(ActivityView.ActivityEntry entry) {
      return new Suggestion(entry.userQuestion(), entry.finalAnswer());
    }
  }

  @Get("/activities/{userId}")
  public ActivitiesList listActivities(String userId) {
      var viewResult =  componentClient
            .forView()
            .method(ActivityView::getActivities)
            .invoke(userId);

    return ActivitiesList.fromView(viewResult);
  }
```

## <a href="about:blank#_running_the_service"></a> Running the service

Start your service locally:

```command
mvn compile exec:java
```
Ask for activities.

```command
curl -i -XPOST --location "http://localhost:9000/activities/alice" \
  --header "Content-Type: application/json" \
  --data '{"message": "I am in Madrid. What should I do? Beware of the weather."}'
```
Retrieve the suggested activities with the new list method:

```command
curl -i -XGET --location "http://localhost:9000/activities/alice"
```
Make another request for activities:

```command
curl -i -XPOST --location "http://localhost:9000/activities/alice" \
  --header "Content-Type: application/json" \
  --data '{"message": "I am in Stockholm. What should I do? Take the weather into account."}'
```
The list should include suggested activities for both Madrid and Stockholm:

```command
curl -i -XGET --location "http://localhost:9000/activities/alice"
```
If you have `jq` installed you can format the json response with:

```command
curl http://localhost:9000/activities/alice | jq
```

## <a href="about:blank#_next_steps"></a> Next steps

- In a larger system with more agents, we could benefit from letting the AI model come up with a plan of which agents to use and in which order to execute. Continue with [Dynamic orchestration](dynamic-team.html)
- Learn more about the <a href="../../java/views.html">`View` component</a>.

<!-- <footer> -->
<!-- <nav> -->
[Orchestrate the agents](team.html) [Dynamic orchestration](dynamic-team.html)
<!-- </nav> -->

<!-- </footer> -->

<!-- <aside> -->

<!-- </aside> -->