(function() {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('updatableDate', function($interval, UtilsSrv) {
            function updateTime(scope) {
                if (scope.dateNow) {
                    var now = moment();
                    // now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
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

            return {
                'restrict': 'E',
                'link': function(scope, element, attrs, ctrl, transclude) {
                    UtilsSrv.updatableLink(scope, element, attrs, ctrl, transclude);
                    $(element).find('.input-datetime').datetimepicker({
                        format: 'dd-mm-yyyy hh:ii',
                        weekStart: 1,
                        startView: 1,
                        todayBtn: true,
                        language: 'fr',
                        autoclose: true
                    });
                    scope.dateNow = false;
                    scope.timeUpdater = undefined;
                    if (angular.isString(scope.value)) {
                        var m = moment(scope.value, 'YYYYMMDDTHHmmssZZ');
                        if (m.isValid()) {
                            scope.humanDate = m.format('DD-MM-YYYY HH:mm');
                        }
                    }
                    scope.$watch('dateNow', function() {
                        updateTime(scope);
                    });
                    scope.$watch('humanDate', function() {
                        if (angular.isString(scope.humanDate)) {
                            var m = moment(scope.humanDate, 'DD-MM-YYYY HH:mm');
                            if (m.isValid()) {
                                scope.value = m.format('YYYYMMDDTHHmmssZZ');
                            }
                        }
                    });
                },
                'templateUrl': 'views/directives/updatable-date.html',
                'scope': {
                    'value': '=?',
                    'onUpdate': '&',
                    'active': '=?'
                }
            };
        });
})();
