(function () {
    'use strict';
    angular.module('theHiveControllers').controller('CaseTasksItemCtrl',
        function ($scope, $rootScope, $state, $stateParams, $timeout, $uibModal, SecuritySrv, ModalSrv, CaseSrv, AuthenticationSrv, OrganisationSrv, CaseTabsSrv, CaseTaskSrv, PSearchSrv, TaskLogSrv, NotificationSrv, CortexSrv, StatSrv, task) {
            var caseId = $stateParams.caseId,
                taskId = $stateParams.itemId;

            // Initialize controller
            $scope.task = task;
            $scope.tabName = 'task-' + task.id;
            $scope.taskResponders = null;

            $scope.loading = false;
            $scope.newLog = {
                message: ''
            };
            $scope.sortOptions = {
                '+startDate': 'Oldest first',
                '-startDate': 'Newest first'
            };
            $scope.state = {
                editing: false,
                isCollapsed: false,
                dropdownOpen: false,
                attachmentCollapsed: true,
                logMissing: '',
                sort: '-startDate'
            };

            $scope.markdownEditorOptions = {
                iconlibrary: 'fa',
                addExtraButtons: true,
                resize: 'vertical'
            };

            $scope.initScope = function () {

                $scope.logs = PSearchSrv(caseId, 'case_task_log', {
                    scope: $scope,
                    filter: {
                        _and: [{
                            _parent: {
                                _type: 'case_task',
                                _query: {
                                    _id: taskId
                                }
                            }
                        }, {
                            _not: {
                                'status': 'Deleted'
                            }
                        }]
                    },
                    'sort': $scope.state.sort,
                    'pageSize': 10,
                    onUpdate: function() {
                        var ids = _.pluck($scope.logs.values, 'id');

                        StatSrv.getPromise({
                            objectType: 'connector/cortex/action',
                            field: 'objectId',
                            limit: 1000,
                            skipTotal: true,
                            query: {
                              _and: [{
                                  _field: 'objectType',
                                  _value: 'case_task_log'
                              },
                              {
                                  _in: {
                                      _field: 'objectId',
                                      _values: ids
                                  }
                              }]
                            }
                        }).then(function(response) {
                            var counts = response.data;
                            _.each($scope.logs.values, function(log) {
                                log.nbActions = counts[log.id] ? counts[log.id].count : 0;
                            });
                        });
                    }
                });

                var connectors = $scope.appConfig.connectors;
                if(connectors.cortex && connectors.cortex.enabled) {
                    $scope.actions = PSearchSrv(null, 'connector/cortex/action', {
                        scope: $scope,
                        streamObjectType: 'action',
                        filter: {
                            _and: [
                                {
                                    _not: {
                                        status: 'Deleted'
                                    }
                                }, {
                                    objectType: 'case_task'
                                }, {
                                    objectId: taskId
                                }
                            ]
                        },
                        sort: ['-startDate'],
                        pageSize: 100,
                        guard: function(updates) {
                            return _.find(updates, function(item) {
                                return (item.base.object.objectType === 'case_task') && (item.base.object.objectId === taskId);
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
                    taskId: $scope.task.id
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
                var taskId = $scope.task.id;

                CaseTaskSrv.update({
                    'taskId': taskId
                }, {
                    'status': 'InProgress'
                }, function(data) {
                    $scope.task = data;
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
                    'taskId': $scope.task.id
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
                CortexSrv.getResponders('case_task', $scope.task.id)
                  .then(function(responders) {
                      $scope.taskResponders = responders;
                  })
                  .catch(function(err) {
                      NotificationSrv.error('taskDetails', err.data, err.status);
                  });
            };

            $scope.runResponder = function(responderId, responderName) {
                CortexSrv.runResponder(responderId, responderName, 'case_task', _.pick($scope.task, 'id'))
                  .then(function(response) {
                      NotificationSrv.log(['Responder', response.data.responderName, 'started successfully on task', $scope.task.title].join(' '), 'success');
                  })
                  .catch(function(response) {
                      if(response && !_.isString(response)) {
                          NotificationSrv.error('taskDetails', response.data, response.status);
                      }
                  });
            };

            $scope.reloadTask = function() {
                CaseTaskSrv.get({
                    'taskId': $scope.task.id
                }, function(data) {
                    $scope.task = data;
                }, function(response) {
                    NotificationSrv.error('taskDetails', response.data, response.status);
                });
            };

            $scope.loadShares = function () {
                return CaseTaskSrv.getShares(caseId, taskId)
                    .then(function(response) {
                        $scope.shares = response.data;
                    });
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
                        return CaseTaskSrv.removeShare($scope.task.id, share);
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

            this.$onInit = function() {
                // Add tabs
                CaseTabsSrv.addTab($scope.tabName, {
                    name: $scope.tabName,
                    label: task.title,
                    closable: true,
                    state: 'app.case.tasks-item',
                    params: {
                        itemId: task.id
                    }
                });

                // Select tab
                $timeout(function() {
                    CaseTabsSrv.activateTab($scope.tabName);
                    $('html,body').animate({scrollTop: $('body').offset().top}, 'fast');
                }, 0);


                // Prepare the scope data
                $scope.initScope(task);

                if(SecuritySrv.checkPermissions(['manageShare'], $scope.userPermissions)) {
                    $scope.loadShares();
                }

                // $scope.organisations = organisations;
                // $scope.profiles = profiles;
                // $scope.shares = shares;
            };
        }
    );
}());
