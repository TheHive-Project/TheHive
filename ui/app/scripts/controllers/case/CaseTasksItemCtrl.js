(function () {
    'use strict';
    angular.module('theHiveControllers').controller('CaseTasksItemCtrl',
        function ($scope, $rootScope, $state, $stateParams, $timeout, CaseTabsSrv, CaseTaskSrv, PSearchSrv, TaskLogSrv, AlertSrv, task) {
            var caseId = $stateParams.caseId,
                taskId = $stateParams.itemId;

            // Initialize controller
            $scope.task = task;
            $scope.tabName = 'task-' + task.id;

            $scope.loading = false;
            $scope.newLog = {
                message: ''
            };
            $scope.state = {
                editing: false,
                isCollapsed: false,
                dropdownOpen: false,
                attachmentCollapsed: true,
                logMissing: ''
            };

            $scope.markdownEditorOptions = {
                iconlibrary: 'fa',
                addExtraButtons: true,
                resize: 'vertical'
            };

            $scope.initScope = function () {

                $scope.logs = PSearchSrv(caseId, 'case_task_log', {
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
                    'sort': '-startDate'
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
                    AlertSrv.error('taskDetails', response.data, response.status);
                });
            };

            $scope.complete = function () {
                $scope.task.status = 'Completed';
                $scope.updateField('status', 'Completed');

                CaseTabsSrv.removeTab($scope.tabName);
                $state.go('app.case.tasks', {
                    caseId: $scope.caseId
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
                    delete $scope.newLog.attachment;
                    $scope.state.attachmentCollapsed = true;
                    $scope.newLog.message = '';

                    $rootScope.markdownEditorObjects.newLog.hidePreview();
                    $scope.adding = false;
                    // removeAllFiles is added by dropzone directive as control
                    $scope.state.removeAllFiles();

                    $scope.loading = false;
                }, function (response) {
                    AlertSrv.error('taskDetails', response.data, response.status);
                    $scope.loading = false;
                });

                return true;
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
