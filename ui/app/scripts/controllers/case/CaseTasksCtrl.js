(function() {
    'use strict';
    angular.module('theHiveControllers')
        .controller('CaseTaskDeleteCtrl', CaseTaskDeleteCtrl)
        .controller('CaseTasksCtrl', CaseTasksCtrl);

    function CaseTasksCtrl($scope, $state, $stateParams, $q, $uibModal, CaseTabsSrv, PSearchSrv, CaseTaskSrv, UserInfoSrv, NotificationSrv) {

        CaseTabsSrv.activateTab($state.current.data.tab);

        $scope.caseId = $stateParams.caseId;
        $scope.state = {
            'isNewTask': false
        };
        $scope.newTask = {
            status: 'Waiting'
        };

        $scope.tasks = PSearchSrv($scope.caseId, 'case_task', {
            scope: $scope,
            baseFilter: {
                '_and': [{
                    '_parent': {
                        '_type': 'case',
                        '_query': {
                            '_id': $scope.caseId
                        }
                    }
                }, {
                    '_not': {
                        'status': 'Cancel'
                    }
                }]
            },
            sort: ['-flag', '+order', '+startDate', '+title'],
            pageSize: 30
        });

        $scope.showTask = function(task) {
            $state.go('app.case.tasks-item', {
                itemId: task.id
            });
        };

        $scope.updateField = function (fieldName, newValue, task) {
            var field = {};
            field[fieldName] = newValue;
            return CaseTaskSrv.update({
                taskId: task.id
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
            }, function(response) {
                NotificationSrv.error('taskList', response.data, response.status);
            });
        };

        $scope.removeTask = function(task) {

            var modalInstance = $uibModal.open({
                animation: true,
                templateUrl: 'views/partials/case/case.task.delete.html',
                controller: 'CaseTaskDeleteCtrl',
                controllerAs: 'vm',
                resolve: {
                    title: function() {
                        return task.title;
                    }
                }
            });

            modalInstance.result.then(function() {
                CaseTaskSrv.update({
                    'taskId': task.id
                }, {
                    status: 'Cancel'
                }, function() {
                    $scope.$emit('tasks:task-removed', task);
                }, function(response) {
                    NotificationSrv.error('taskList', response.data, response.status);
                });
            });

        };

        // open task tab with its details
        $scope.startTask = function(task) {
            if (task.status === 'Waiting') {
                $scope.updateTaskStatus(task.id, 'InProgress')
                    .then($scope.showTask);
            } else {
                $scope.showTask(task);
            }
        };

        $scope.openTask = function(task) {
            if (task.status === 'Completed') {
                $scope.updateTaskStatus(task.id, 'InProgress')
                    .then($scope.showTask);
            }
        };

        $scope.closeTask = function(task) {
            if (task.status === 'InProgress') {
                $scope.updateTaskStatus(task.id, 'Completed');
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

    }

    function CaseTaskDeleteCtrl($uibModalInstance, title) {
        this.title = title;

        this.ok = function() {
            $uibModalInstance.close();
        };

        this.cancel = function() {
            $uibModalInstance.dismiss();
        };
    }
}());
