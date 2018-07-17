(function() {
    angular.module('theHiveDirectives')
        .directive('multiRowAreaFormatterNumber', function() {
          return {
            require: 'ngModel',
            link: function(scope, element, attrs, controller) {
                controller.$formatters.push(function(value) {
                    return value ? value.map(String).join('\n') : null;
                });
                controller.$parsers.push(function(value) {
                    return value ? value.split('\n').map(Number) : [null];
                });
            }
          };
        });
})();