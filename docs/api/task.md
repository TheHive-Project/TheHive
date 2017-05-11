# Task

## Model definition

Required attributes:
 - `title` (text) : title of the task
 - `status` (taskStatus) : status of the task (*Waiting*, *InProgress*, *Completed* or *Cancel*) **default=Waiting**
 - `flag` (boolean) : flag of the task **default=false**

Optional attributes:
 - `owner` (string) : user who owns the task. This is automatically set to current user when status is set to
 *InProgress*
 - `description` (text) : task details
 - `startDate` (date) : date of the beginning of the task. This is automatically set when status is set to *Open*
 - `endDate` (date) : date of the end of the task. This is automatically set when status is set to *Completed*

