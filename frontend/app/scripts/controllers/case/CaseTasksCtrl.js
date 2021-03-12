(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('CaseTasksCtrl', CaseTasksCtrl);

    function CaseTasksCtrl($scope, $state, $stateParams, $q, $uibModal, AuthenticationSrv, ModalUtilsSrv, FilteringSrv, CaseTabsSrv, PaginatedQuerySrv, CaseTaskSrv, UserSrv, NotificationSrv, CortexSrv, AppLayoutSrv) {

        CaseTabsSrv.activateTab($state.current.data.tab);

        $scope.caseId = $stateParams.caseId;
        $scope.state = {
            isNewTask: false,
            showGrouped: !!AppLayoutSrv.layout.groupTasks
        };
        $scope.newTask = {
            status: 'Waiting'
        };
        $scope.taskResponders = null;
        $scope.collapseOptions = {};

        $scope.selection = [];
        $scope.menu = {
            selectAll: false
        };

        this.$onInit = function() {
            $scope.filtering = new FilteringSrv('task', 'task.list', {
                version: 'v1',
                defaults: {
                    showFilters: true,
                    showStats: false,
                    pageSize: 15,
                    sort: ['-flag', '+order', '+startDate', '+title'],
                },
                defaultFilter: []
            });

            $scope.filtering.initContext($scope.caseId)
                .then(function() {
                    $scope.load();

                    $scope.$watchCollection('tasks.pageSize', function (newValue) {
                        $scope.filtering.setPageSize(newValue);
                    });
                });
        };

        $scope.getAssignableUsers = function(taskId) {
            return [
                {_name: 'getTask', idOrName: taskId},
                {_name: 'assignableUsers'}
            ];
        };

        $scope.load = function() {
            $scope.list = new PaginatedQuerySrv({
                name: 'case-tasks',
                root: $scope.caseId,
                objectType: 'case_task',
                version: 'v1',
                scope: $scope,
                sort: $scope.filtering.context.sort,
                loadAll: false,
                pageSize: $scope.filtering.context.pageSize,
                filter: $scope.filtering.buildQuery(),
                baseFilter: {
                    _not: {
                        _field: 'status',
                        _value: 'Cancel'
                    }
                },
                operations: [
                    {'_name': 'getCase', "idOrName": $scope.caseId},
                    {'_name': 'tasks'}
                ],
                extraData: ['shareCount', 'actionRequired'],
                //extraData: ['isOwner', 'shareCount'],
                onUpdate: function() {
                    $scope.buildTaskGroups($scope.list.values);
                    $scope.resetSelection();
                }
            });
        };

        $scope.resetSelection = function() {
            if ($scope.menu.selectAll) {
                $scope.selectAll();
            } else {
                $scope.selection = [];
                $scope.menu.selectAll = false;
                $scope.updateMenu();
            }
        };

        $scope.updateMenu = function() {
            // Handle flag/unflag menu items
            var temp = _.uniq(_.pluck($scope.selection, 'flag'));
            $scope.menu.unflag = temp.length === 1 && temp[0] === true;
            $scope.menu.flag = temp.length === 1 && temp[0] === false;

            // Handle close menu item
            // temp = _.uniq(_.pluck($scope.selection, 'status'));
            // $scope.menu.close = temp.length === 1 && temp[0] === 'Open';
            // $scope.menu.reopen = temp.length === 1 && temp[0] === 'Resolved';

            // $scope.menu.delete = $scope.selection.length > 0;
        };

        $scope.select = function(task) {
            if (task.selected) {
                $scope.selection.push(task);
            } else {
                $scope.selection = _.reject($scope.selection, function(item) {
                    return item._id === task._id;
                });
            }
            $scope.updateMenu();
        };

        $scope.selectAll = function() {
            var selected = $scope.menu.selectAll;

            _.each($scope.list.values, function(item) {
                // if(SecuritySrv.checkPermissions(['manageCase'], item.extraData.permissions)) {
                item.selected = selected;
                // }
            });

            if (selected) {
                $scope.selection = _.filter($scope.list.values, function(item) {
                    return !!item.selected;
                });
            } else {
                $scope.selection = [];
            }

            $scope.updateMenu();
        };

        // ######################@@@

        $scope.toggleStats = function () {
            $scope.filtering.toggleStats();
        };

        $scope.toggleFilters = function () {
            $scope.filtering.toggleFilters();
        };

        $scope.filter = function () {
            $scope.filtering.filter().then($scope.applyFilters);
        };

        $scope.clearFilters = function () {
            $scope.filtering.clearFilters()
                .then($scope.search);
        };

        $scope.removeFilter = function (index) {
            $scope.filtering.removeFilter(index)
                .then($scope.search);
        };

        $scope.search = function () {
            $scope.load();
            $scope.filtering.storeContext();
        };
        $scope.addFilterValue = function (field, value) {
            $scope.filtering.addFilterValue(field, value);
            $scope.search();
        };

        $scope.filterBy = function(field, value) {
            $scope.filtering.clearFilters()
                .then(function() {
                    $scope.addFilterValue(field, value);
                });
        };

        $scope.filterMyTasks = function() {
            $scope.filtering.clearFilters()
                .then(function() {
                    var currentUser = AuthenticationSrv.currentUser;
                    $scope.filtering.addFilter({
                        field: 'assignee',
                        type: 'user',
                        value: {
                            list: [{
                                text: currentUser.login,
                                label: currentUser.name
                            }]
                        }
                    });
                    $scope.search();
                });
        };

        $scope.toggleGroupedView = function() {
            $scope.state.showGrouped = !$scope.state.showGrouped;

            AppLayoutSrv.groupTasks($scope.state.showGrouped);
        };

        $scope.buildTaskGroups = function(tasks) {
            // Sort tasks by order
            var orderedTasks = _.sortBy(_.map(tasks, function(t) {
                return _.pick(t, 'group', 'order');
            }), 'order');
            var groups = [];

            // Get group names by keeping the group orders
            _.each(orderedTasks, function(task) {
                if(groups.indexOf(task.group) === -1) {
                    groups.push(task.group);
                }
            });

            var groupedTasks = [];
            _.each(groups, function(group) {
                groupedTasks.push({
                    group: group,
                    tasks: _.filter(tasks, function(t) {
                        return t.group === group;
                    })
                });
            });

            $scope.groups = groups;
            $scope.groupedTasks = groupedTasks;
        };

        $scope.showTask = function(taskId) {
            $state.go('app.case.tasks-item', {
                itemId: taskId
            });
        };

        $scope.updateField = function (fieldName, newValue, task) {
            var field = {};
            field[fieldName] = newValue;
            return CaseTaskSrv.update({
                taskId: task._id
            }, field, function () {}, function (response) {
                NotificationSrv.error('taskList', response.data, response.status);
            });
        };

        $scope.addTask = function() {
            CaseTaskSrv.save({
                'caseId': $scope.caseId,
                'flag': false
            }, $scope.newTask, function() {
                $scope.isNewTask = false;
                $scope.newTask.title = '';
                $scope.newTask.group = '';
                NotificationSrv.success('Task has been successfully added');
            }, function(response) {
                NotificationSrv.error('taskList', response.data, response.status);
            });
        };

        $scope.removeTask = function(task) {

            ModalUtilsSrv.confirm('Delete task', 'Are you sure you want to delete the selected task?', {
                okText: 'Yes, remove it',
                flavor: 'danger'
            }).then(function() {
                CaseTaskSrv.update({
                    'taskId': task._id
                }, {
                    status: 'Cancel'
                }, function() {
                    $scope.$emit('tasks:task-removed', task);
                    NotificationSrv.success('Task has been successfully removed');
                }, function(response) {
                    NotificationSrv.error('taskList', response.data, response.status);
                });
            });
        };

        $scope.bulkFlag = function(flag) {
            var ids = _.pluck($scope.selection, '_id');

            return CaseTaskSrv.bulkUpdate(ids, {flag: flag})
                .then(function(/*responses*/) {
                    NotificationSrv.log('Selected tasks have been updated successfully', 'success');
                })
                .catch(function(err) {
                    NotificationSrv.error('Bulk flag tasks', err.data, err.status);
                });
        }

        // open task tab with its details
        $scope.startTask = function(task) {
            var taskId = task._id;

            if (task.status === 'Waiting') {
                $scope.updateTaskStatus(taskId, 'InProgress')
                    .then(function(/*response*/) {
                        $scope.showTask(taskId);
                    });
            } else {
                $scope.showTask(taskId);
            }
        };

        $scope.openTask = function(task) {
            if (task.status === 'Completed') {
                $scope.updateTaskStatus(task._id, 'InProgress')
                    .then(function(/*response*/) {
                        $scope.showTask(task._id);
                    });
            }
        };

        $scope.closeTask = function(task) {
            if (task.status === 'InProgress') {
                $scope.updateTaskStatus(task._id, 'Completed')
                    .then(function() {
                        NotificationSrv.success('Task has been successfully closed');
                    });
            }
        };

        $scope.updateTaskStatus = function(taskId, status) {
            var defer = $q.defer();

            CaseTaskSrv.update({
                'taskId': taskId
            }, {
                'status': status
            }, function(data) {
                defer.resolve(data);
            }, function(response) {
                NotificationSrv.error('taskList', response.data, response.status);
                defer.reject(response);
            });

            return defer.promise;
        };

        $scope.getTaskResponders = function(task, force) {
            if(!force && $scope.taskResponders !== null) {
               return;
            }

            $scope.taskResponders = null;
            CortexSrv.getResponders('case_task', task._id)
              .then(function(responders) {
                  $scope.taskResponders = responders;
                  return CortexSrv.promntForResponder(responders);
              })
              .then(function(response) {
                  if(response && _.isString(response)) {
                      NotificationSrv.log(response, 'warning');
                  } else {
                      return CortexSrv.runResponder(response.id, response.name, 'case_task', _.pick(task, '_id'));
                  }
              })
              .then(function(response){
                  NotificationSrv.success(['Responder', response.data.responderName, 'started successfully on task', task.title].join(' '));
              })
              .catch(function(err) {
                  if(err && !_.isString(err)) {
                      NotificationSrv.error('taskList', err.data, err.status);
                  }
              });
        };

        // $scope.runResponder = function(responderId, responderName, task) {
        //     CortexSrv.runResponder(responderId, responderName, 'case_task', _.pick(task, '_id'))
        //       .then(function(response) {
        //           NotificationSrv.success(['Responder', response.data.responderName, 'started successfully on task', task.title].join(' '));
        //       })
        //       .catch(function(response) {
        //           if(response && !_.isString(response)) {
        //               NotificationSrv.error('taskList', response.data, response.status);
        //           }
        //       });
        // };
    }
}());
