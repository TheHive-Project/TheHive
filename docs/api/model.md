# TheHive Model Definition

## Field Types

 - `string` : textual data (example "malware").
 - `text` : textual data. The difference between `string` and `text` is in the way content can be searched.`string` is
 searchable as-is whereas `text`,  words (token) are searchable, not the whole content (example "Ten users have received
 this ransomware").
 - `date` : date and time using timestamps with milliseconds format.
 - `boolean` : true or false
 - `number` : numeric value
 - `metrics` : JSON object that contains only numbers

Field can be prefixed with `multi-` in order to indicate that multiple values can be provided.

## Common Attributes

All entities share the following attributes:
 - `createdBy` (text) : login of the user who create the entity
 - `createdAt` (date) : date and time of the creation
 - `updatedBy` (text) : login of the user who do the last update of the entity
 - `upadtedAt` (date) : date and time of the last update
 - `user` (text) : same value as `createdBy` (this field is deprecated)
This attributes are handled by back-end and can't be directly updated.


