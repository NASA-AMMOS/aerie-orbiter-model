import requests

api_url = 'http://localhost:8080/v1/graphql' # https://aerie-dev.jpl.nasa.gov:8080/v1/graphql
jar_name = "missionmodel.jar"

response = requests.post(
    url=api_url,
    headers={ 'x-hasura-admin-secret': 'aerie' },
    json={
        'query': '''
        mutation UpdateUploadedJars($path: bytea) {
          update_uploaded_file_many(updates:{ _set: {path: $path}, where: {name: {_like: "%.jar"}}}) {
            affected_rows
          }
        }''',
        'variables': {
            "path": jar_name
        },
    },
    verify=False
)

print(response.json())
