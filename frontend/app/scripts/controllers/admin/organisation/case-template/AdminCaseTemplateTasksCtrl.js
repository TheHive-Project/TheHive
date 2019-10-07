(function() {
    'use strict';

    angular.module('theHiveControllers').controller('AdminCaseTemplateTasksCtrl', function($scope, $uibModalInstance, action, task, users, groups) {
        $scope.task = task || {};
        $scope.action = action;
        $scope.users = users;
        $scope.groups = groups;

        $scope.cancel = function() {
            $uibModalInstance.dismiss();
        };

        $scope.addTask = function() {
            $uibModalInstance.close(task);
        };
    });
})();
