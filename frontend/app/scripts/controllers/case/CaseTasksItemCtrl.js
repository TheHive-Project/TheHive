(function () {
    'use strict';
    angular.module('theHiveControllers').controller('CaseTasksItemCtrl',
        function ($q, $scope, $rootScope, $state, $stateParams, $timeout, $uibModal, StreamSrv, PaginatedQuerySrv, SecuritySrv, ModalSrv, CaseSrv, AuthenticationSrv, OrganisationSrv, CaseTabsSrv, CaseTaskSrv, PSearchSrv, TaskLogSrv, NotificationSrv, CortexSrv, StatSrv, task) {
            var caseId = $stateParams.caseId,
                taskId = $stateParams.itemId;

            // Initialize controller
            $scope.task = task;
            $scope.tabName = 'task-' + task._id;
            $scope.taskResponders = null;

            $scope.assignableUsersQuery = [
                {_name: 'getTask', idOrName: task._id},
                {_name: 'assignableUsers'}
            ];

            $scope.loading = false;
            $scope.newLog = {
                message: ''
            };
            $scope.sortOptions = {
                '+date': 'Oldest first',
                '-date': 'Newest first'
            };
            $scope.state = {
                editing: false,
                isCollapsed: false,
                dropdownOpen: false,
                attachmentCollapsed: true,
                logMissing: '',
                sort: '-date'
            };

            $scope.markdownEditorOptions = {
                iconlibrary: 'fa',
                addExtraButtons: true,
                resize: 'vertical'
            };

            $scope.initScope = function () {

                $scope.logs = new PaginatedQuerySrv({
                    name: 'case-task-logs',
                    root: caseId,
                    objectType: 'case_task_log',
                    version: 'v1',
                    scope: $scope,
                    sort: $scope.state.sort,
                    loadAll: false,
                    pageSize: 10,
                    operations: [
                        {
                            '_name': 'getTask',
                            'idOrName': taskId
                        },
                        {
                            '_name': 'logs'
                        }
                    ],
                    extraData: ['actionCount']
                });

                var connectors = $scope.appConfig.connectors;
                if(connectors.cortex && connectors.cortex.enabled) {
                    $scope.actions = new PaginatedQuerySrv({
                        name: 'case-task-actions',
                        version: 'v1',
                        scope: $scope,
                        streamObjectType: 'action',
                        loadAll: true,
                        sort: ['-startDate'],
                        pageSize: 100,
                        operations: [
                            { '_name': 'getTask', 'idOrName': taskId },
                            { '_name': 'actions' }
                        ],
                        guard: function(updates) {
                            return _.find(updates, function(item) {
                                return (item.base.details.objectType === 'Task') && (item.base.details.objectId === taskId);
                            }) !== undefined;
                        }
                    });
                }
            };

            $scope.switchFlag = function () {
                if ($scope.task.flag === undefined || $scope.task.flag === false) {
                    $scope.task.flag = true;
                    $scope.updateField('flag', true);
                } else {
                    $scope.task.flag = false;
                    $scope.updateField('flag', false);
                }
            };

            $scope.updateField = function (fieldName, newValue) {
                var field = {};
                field[fieldName] = newValue;
                return CaseTaskSrv.update({
                    taskId: $scope.task._id
                }, field, function () {}, function (response) {
                    NotificationSrv.error('taskDetails', response.data, response.status);
                });
            };

            $scope.closeTask = function () {
                $scope.task.status = 'Completed';
                $scope.updateField('status', 'Completed');

                CaseTabsSrv.removeTab($scope.tabName);
                $state.go('app.case.tasks', {
                    caseId: $scope.caseId
                });
            };

            $scope.openTask = function() {
                $scope.task.status = 'InProgress';
                $scope.updateField('status', 'InProgress');
            };

            $scope.startTask = function() {
                var taskId = $scope.task._id;

                CaseTaskSrv.update({
                    'taskId': taskId
                }, {
                    'status': 'InProgress'
                }, function(/*data*/) {
                    // $scope.task = data;
                    $scope.reloadTask();
                }, function(response) {
                    NotificationSrv.error('taskDetails', response.data, response.status);
                });
            };

            $scope.showLogEditor = function () {
                $scope.adding = true;
                $rootScope.$broadcast('beforeNewLogShow');
            };

            $scope.cancelAddLog = function() {
                // Switch to editor mode instead of preview mode
                $rootScope.markdownEditorObjects.newLog.hidePreview();
                $scope.adding = false;
            };

            $scope.addLog = function () {
                $scope.loading = true;

                if ($scope.state.attachmentCollapsed || !$scope.newLog.attachment) {
                    delete $scope.newLog.attachment;
                }

                TaskLogSrv.save({
                    'taskId': $scope.task._id
                }, $scope.newLog, function () {
                    if($scope.task.status === 'Waiting') {
                        // Reload the task
                        $scope.reloadTask();
                    }

                    delete $scope.newLog.attachment;
                    $scope.state.attachmentCollapsed = true;
                    $scope.newLog.message = '';

                    $rootScope.markdownEditorObjects.newLog.hidePreview();
                    $scope.adding = false;
                    // removeAllFiles is added by dropzone directive as control
                    $scope.state.removeAllFiles();

                    $scope.loading = false;
                }, function (response) {
                    NotificationSrv.error('taskDetails', response.data, response.status);
                    $scope.loading = false;
                });

                return true;
            };

            $scope.sortBy = function(sort) {
                $scope.state.sort = sort;
                $scope.logs.sort = sort;
                $scope.logs.update();
            };

            $scope.scrollTo = function(hash) {
                $timeout(function() {
                    var el = angular.element(hash)[0];

                    // Scrolling hack using jQuery stuff
                    $('html,body').animate({
                        scrollTop: $(el).offset().top
                    }, 'fast');
                }, 100);
            };

            $scope.getTaskResponders = function(force) {
                if(!force && $scope.taskResponders !== null) {
                   return;
                }

                $scope.taskResponders = null;
                CortexSrv.getResponders('case_task', $scope.task._id)
                  .then(function(responders) {
                      $scope.taskResponders = responders;
                      return CortexSrv.promntForResponder(responders);
                  })
                  .then(function(response) {
                      if(response && _.isString(response)) {
                          NotificationSrv.log(response, 'warning');
                      } else {
                          return CortexSrv.runResponder(response.id, response.name, 'case_task', _.pick($scope.task, '_id'));
                      }
                  })
                  .then(function(response){
                      NotificationSrv.log(['Responder', response.data.responderName, 'started successfully on task', $scope.task.title].join(' '), 'success');
                  })
                  .catch(function(err) {
                      if(err && !_.isString(err)) {
                          NotificationSrv.error('taskDetails', err.data, err.status);
                      }
                  });
            };

            $scope.reloadTask = function() {
                return CaseTaskSrv.getById($scope.task._id)
                    .then(function(data) {
                        $scope.task = data;
                    })
                    .catch(function(response) {
                        NotificationSrv.error('taskDetails', response.data, response.status);
                    });
            };

            $scope.loadShares = function () {
                if(SecuritySrv.checkPermissions(['manageShare'], $scope.userPermissions)) {
                    return CaseTaskSrv.getShares(caseId, taskId)
                        .then(function(response) {

                            // Add action required flag to shares
                            _.each(response.data, function(share) {
                                share.actionRequired = !!$scope.task.extraData.actionRequiredMap[share.organisationName];
                            });

                            $scope.shares = response.data;
                        });
                }
            };

            $scope.removeShare = function(share) {
                var modalInstance = ModalSrv.confirm(
                    'Remove task share',
                    'Are you sure you want to remove this sharing rule?', {
                        okText: 'Yes, remove it',
                        flavor: 'danger'
                    }
                );

                modalInstance.result
                    .then(function() {
                        return CaseTaskSrv.removeShare($scope.task._id, share);
                    })
                    .then(function(/*response*/) {
                        $scope.loadShares();
                        NotificationSrv.log('Task sharings updated successfully', 'success');
                    })
                    .catch(function(err) {
                        if(err && !_.isString(err)) {
                            NotificationSrv.error('Error', 'Task sharings update failed', err.status);
                        }
                    });
            };

            $scope.addTaskShare = function() {
                var modalInstance = $uibModal.open({
                    animation: true,
                    templateUrl: 'views/components/sharing/sharing-modal.html',
                    controller: 'SharingModalCtrl',
                    controllerAs: '$modal',
                    size: 'lg',
                    resolve: {
                        shares: function() {
                            return CaseSrv.getShares(caseId)
                                .then(function(response) {
                                    var caseShares = response.data;
                                    var taskShares = _.pluck($scope.shares, 'organisationName');

                                    var shares = _.filter(caseShares, function(item) {
                                        return taskShares.indexOf(item.organisationName) === -1;
                                    });

                                    return angular.copy(shares);
                                });
                        },

                    }
                });

                modalInstance.result
                    .then(function(orgs) {
                        return CaseTaskSrv.addShares(taskId, orgs);
                    })
                    .then(function(/*response*/) {
                        $scope.loadShares();
                        NotificationSrv.log('Task sharings updated successfully', 'success');
                    })
                    .catch(function(err) {
                        if(err && !_.isString(err)) {
                            NotificationSrv.error('Error', 'Task sharings update failed', err.status);
                        }
                    });
            };



            $scope.showAddLog = function(prompt) {
                var modalInstance = $uibModal.open({
                    animation: true,
                    keyboard: false,
                    backdrop: 'static',
                    templateUrl: 'views/partials/case/tasklogs/add-task-log.modal.html',
                    controller: 'AddTaskLogModalCtrl',
                    controllerAs: '$modal',
                    size: 'lg',
                    resolve: {
                        task: task,
                        config: function() {
                            return {
                                prompt: prompt
                            };
                        }
                    }
                });

                return modalInstance.result;
            };

            $scope.markAsDone = function(task) {
                CaseTaskSrv.promtForActionRequired('Require Action', 'Would you like to add a task log before marking the required action as DONE?')
                    .then(function(response) {
                        if(response === 'skip-log') {
                            return $q.resolve();
                        } else {
                            return $scope.showAddLog('Please add a task log');
                        }
                    })
                    .then(function() {
                        return CaseTaskSrv.markAsDone(task._id, $scope.currentUser.organisation);
                    })
                    .then(function() {
                        NotificationSrv.log('The task\'s required action is completed', 'success');
                    })
                    .catch(function(err) {
                        if(err && !_.isString(err)) {
                            NotificationSrv.error('Error', 'Task required action failed to be marked as done', err.status);
                        }
                    });
            };

            $scope.markAsActionRequired = function(task) {
                CaseTaskSrv.promtForActionRequired('Require Action', 'Would you like to add a task log before requesting action?')
                    .then(function(response) {
                        if(response === 'skip-log') {
                            return $q.resolve();
                        } else {
                            return $scope.showAddLog('Please add a task log');
                        }
                    })
                    .then(function() {
                        return CaseTaskSrv.markAsActionRequired(task._id, $scope.currentUser.organisation);
                    })
                    .then(function() {
                        NotificationSrv.log('The task\'s required action flag has been set', 'success');
                    })
                    .catch(function(err) {
                        if(err && !_.isString(err)) {
                            NotificationSrv.error('Error', 'Task request action failed', err.status);
                        }
                    });

            };

            $scope.markShareAsActionRequired = function(task, org) {
                CaseTaskSrv.promtForActionRequired('Require Action', 'Would you like to add a task log before marking the required action as DONE?')
                    .then(function(response) {
                        if(response === 'skip-log') {
                            return $q.resolve();
                        } else {
                            return $scope.showAddLog('Please add a task log');
                        }
                    })
                    .then(function() {
                        return CaseTaskSrv.markAsActionRequired(task._id, org);
                    })
                    .then(function() {
                        NotificationSrv.log('The task\'s required action is completed', 'success');
                    })
                    .catch(function(err) {
                        if(err && !_.isString(err)) {
                            NotificationSrv.error('Error', 'Task required action failed to be marked as done', err.status);
                        }
                    });
            };

            $scope.markShareAsActionDone = function(task, org) {
                CaseTaskSrv.promtForActionRequired('Require Action', 'Would you like to add a task log before marking the required action as DONE?')
                    .then(function(response) {
                        if(response === 'skip-log') {
                            return $q.resolve();
                        } else {
                            return $scope.showAddLog('Please add a task log');
                        }
                    })
                    .then(function() {
                        return CaseTaskSrv.markAsDone(task._id, org);
                    })
                    .then(function() {
                        NotificationSrv.log('The task\'s required action is completed', 'success');
                    })
                    .catch(function(err) {
                        if(err && !_.isString(err)) {
                            NotificationSrv.error('Error', 'Task required action failed to be marked as done', err.status);
                        }
                    });
            };

            this.$onInit = function() {
                // Add tabs
                CaseTabsSrv.addTab($scope.tabName, {
                    name: $scope.tabName,
                    label: task.title,
                    closable: true,
                    state: 'app.case.tasks-item',
                    params: {
                        itemId: task._id
                    }
                });

                // Select tab
                $timeout(function() {
                    CaseTabsSrv.activateTab($scope.tabName);
                    $('html,body').animate({scrollTop: $('body').offset().top}, 'fast');
                }, 0);

                // Add action required listener
                StreamSrv.addListener({
                    rootId: caseId,
                    objectType: 'case_task',
                    scope: $scope,
                    callback: function(updates) {
                        // Update action required indicators in task item page and shares list
                        _.each(updates, function(update) {
                            if(update.base.objectId === $scope.task._id ){

                                var updatedKeys = _.keys(update.base.details);

                                var actionRequiredChange = _.find(updatedKeys, function(key) {
                                    return key.startsWith('actionRequired');
                                });

                                if(actionRequiredChange !== undefined) {
                                    $scope.reloadTask()
                                        .then(function() {
                                            $scope.loadShares();
                                        });
                                }
                            }
                        });
                    }
                });

                // Prepare the scope data
                $scope.initScope(task);

                // if(SecuritySrv.checkPermissions(['manageShare'], $scope.userPermissions)) {
                $scope.loadShares();
                //}

                // $scope.organisations = organisations;
                // $scope.profiles = profiles;
                // $scope.shares = shares;
            };
        }
    );
}());
