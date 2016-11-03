(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('taskProgress', function() {
            return {
                restrict: 'E',
                scope: {
                    'tasks': '='
                },
                link: function(scope) {
                    if(scope.tasks) {
                        if (scope.tasks.total === 0) {
                            scope.completed = 'width: 0%';
                            scope.progress = 'width: 0%';
                            scope.waiting = 'width: 0%';
                            scope.cancel = 'width: 0%';
                        } else {
                            scope.completed = 'width: ' + (((scope.tasks.Completed || 0) / scope.tasks.total) * 100) + '%';
                            scope.progress = 'width: ' + (((scope.tasks.InProgress || 0) / scope.tasks.total) * 100) + '%';
                            scope.waiting = 'width: ' + (((scope.tasks.Waiting || 0) / scope.tasks.total) * 100) + '%';
                            scope.cancel = 'width: ' + (((scope.tasks.Cancel || 0) / scope.tasks.total) * 100) + '%';
                        }
                    }
                },
                'templateUrl': 'views/directives/task-progress.html'
            };
        });

})();
