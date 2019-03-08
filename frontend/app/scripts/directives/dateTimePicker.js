(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('dateTimePicker', function($interval, moment) {

        function updateTime(scope) {
            if (scope.dateNow) {
                var now = moment();
                scope.humanDate = now.format('DD-MM-YYYY HH:mm');

                if (!angular.isDefined(scope.timeUpdater)) {
                    scope.timeUpdater = $interval(function() {
                        updateTime(scope);
                    }, 60000);
                }
            } else if (angular.isDefined(scope.timeUpdater)) {
                $interval.cancel(scope.timeUpdater);
                scope.timeUpdater = undefined;
            }
        }

        function link(scope, element) {
            $(element).find('.input-datetime').datetimepicker({
                format: 'dd-mm-yyyy hh:ii',
                weekStart: 1,
                startView: 1,
                todayBtn: true,
                language: 'fr',
                autoclose: true
            });

            scope.dateNow = true;
            scope.timeUpdater = undefined;
            scope.$watch('dateNow', function() {
                updateTime(scope);
            });
            scope.$watch('humanDate', function() {
                scope.isoDate = moment(scope.humanDate, 'DD-MM-YYYY HH:mm').format('YYYYMMDDTHHmmssZZ');
            });
        }

        return {
            restrict: 'EA',
            link: link,
            templateUrl: 'views/directives/date-time-picker.html',
            scope: {
                isoDate: '=date '
            }
        };
    });
})();
