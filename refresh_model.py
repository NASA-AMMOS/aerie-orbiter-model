import requests

api_url = 'http://localhost:8080/v1/graphql' # https://aerie-dev.jpl.nasa.gov:8080/v1/graphql
model_id = 2

def post(query, **variables):
    return requests.post(
        url=api_url,
        headers={ 'x-hasura-admin-secret': 'aerie' },
        json={
            'query': query,
            'variables': variables,
        },
        verify=False
    )


response = post("""
  mutation DeleteAllActivityTypesFromModel($model_id: Int!) {
    delete_activity_type(where:{model_id: {_eq: $model_id}}) {
      affected_rows
    }
    delete_resource_type(where:{model_id: {_eq: $model_id}}) {
      affected_rows
    }
    update_mission_model(where: {id: {_eq: $model_id}}, _inc: {revision: 1}) {
      affected_rows
    }
    update_plan(where: {model_id: {_eq: $model_id}}, _inc: {revision: 1}) {
      affected_rows
    }
  }""", model_id=model_id)
print(response)

response = requests.post("http://localhost:27183/refreshActivityTypes", json={
    "event": {
        "data": {
            "new": {
                "id": model_id
            }
        }
    }
})
print(response)

response = requests.post("http://localhost:27183/refreshModelParameters", json={
    "event": {
        "data": {
            "new": {
                "id": model_id
            }
        }
    }
})
print(response)

response = requests.post("http://localhost:27183/refreshResourceTypes", json={
    "event": {
        "data": {
            "new": {
                "id": model_id
            }
        }
    }
})
print(response)
