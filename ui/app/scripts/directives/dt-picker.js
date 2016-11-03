(function() {
    'use strict';
    angular.module('theHiveDirectives').directive('dtPicker', function() {
        return {
            restrict: 'E',
            scope: {
                'date': '=',
                'placeholder': '@?'
            },
            templateUrl: 'views/directives/dt-picker.html',
            compile: function(element) {
                $(element).find('.input-datetime').datetimepicker({
                    format: 'dd-mm-yyyy',
                    weekStart: 1,
                    startView: 2,
                    minView: 2,
                    autoclose: true
                });

                return function(scope, elem) {
                    if(scope.date){
                        scope.dateValue = moment(scope.date).format('DD-MM-YYYY');
                    }

                    scope.$watch('date', function(date) {
                        if(date) {
                            scope.dateValue = moment(scope.date).format('DD-MM-YYYY');
                        } else {
                            scope.dateValue = null;
                        }
                    });

                    scope.$watch('dateValue', function(dateValue) {
                        var m = moment(dateValue, 'DD-MM-YYYY');
                        if (m.isValid()) {
                            scope.date = m.toDate();
                        } else {
                            scope.date = null;
                            $(elem).find('input').val(null);
                        }
                    });

                    scope.clear = function() {
                        scope.dateValue = null;
                    };
                };
            }
        };
    });

})();
