import json
import requests

api_url = 'http://localhost:8080/v1/graphql' # https://aerie-dev.jpl.nasa.gov:8080/v1/graphql
plan_id = 5

query = '''
  mutation InsertActivities($activities: [activity_directive_insert_input!]!) {
    insert_activity_directive(objects: $activities) {
      returning {
        id
        name
      }
    }
  }
'''

activities = [
  {
    'arguments': {'searchDuration': 86400000000,
                  'observer': "-660",
                  'target': "SUN",
                  'occultingBody': "VENUS",
                  'stepSize': 1800000000,
                  'useDSK': False },
    'metadata': {},
    'name': 'AddSpacecraftEclipses',
    'plan_id': plan_id,
    'start_offset': '00:00:00',
    'type': 'AddSpacecraftEclipses'
  },
  {
    'arguments': {'searchDuration': 86400000000,
                  'observer': "DSS-24",
                  'target': "-660",
                  'occultingBody': "VENUS",
                  'stepSize': 1800000000,
                  'useDSK': False },
    'metadata': {},
    'name': 'AddOccultations',
    'plan_id': plan_id,
    'start_offset': '00:00:00',
    'type': 'AddOccultations'
  },
  {
    'arguments': {'searchDuration': 86400000000,
                  'body': "-660",
                  'target': "VENUS",
                  'stepSize': 1800000000,
                  'maxDistanceFilter': 10000 },
    'metadata': {},
    'name': 'AddPeriapsis',
    'plan_id': plan_id,
    'start_offset': '00:00:00',
    'type': 'AddPeriapsis'
  }
]

response = requests.post(
  url=api_url,
  headers={ 'x-hasura-admin-secret': 'aerie' },
  json={
    'query': query,
    'variables': { "activities": activities },
  },
  verify=False
)

print(json.dumps(response.json(), indent=2))
