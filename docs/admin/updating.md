#  Update TheHive
TheHive is simple to update. You only need to replace your current package files by new ones. If the schema of the data changes between the two versions, the first request to the application asks the user to start a data migration. In this case, authentication is not required.

![update](../files/adminguide_update.png)

This process creates a new index in ElasticSearch (suffixed by the version of the schema) and copies all the data on it (before adapting its format). It is always possible to rollback to the previous version but all modifications done on the new version will be lost.
