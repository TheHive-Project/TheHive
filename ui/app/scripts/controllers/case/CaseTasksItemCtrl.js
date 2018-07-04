(function () {
    'use strict';
    angular.module('theHiveControllers').controller('CaseTasksItemCtrl',
        function ($scope, $rootScope, $state, $stateParams, $timeout, CaseTabsSrv, CaseTaskSrv, PSearchSrv, TaskLogSrv, NotificationSrv, task) {
            var caseId = $stateParams.caseId,
                taskId = $stateParams.itemId;

            // Initialize controller
            $scope.task = task;
            $scope.tabName = 'task-' + task.id;

            $scope.loading = false;
            $scope.newLog = {
                message: (task.template != undefined) ? task.template : ''
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
                    'filter': {
                        '_and': [{
                            '_parent': {
                                '_type': 'case_task',
                                '_query': {
                                    '_id': taskId
                                }
                            }
                        }, {
                            '_not': {
                                'status': 'Deleted'
                            }
                        }]
                    },
                    'sort': $scope.state.sort,
                    'pageSize': 10
                });
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
                    delete $scope.newLog.attachment;
                    $scope.state.attachmentCollapsed = true;
                    $scope.newLog.message = ($scope.task.template != undefined) ? $scope.task.template : '';

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
            }, 0);


            // Prepare the scope data
            $scope.initScope(task);
        }
    );
}());
