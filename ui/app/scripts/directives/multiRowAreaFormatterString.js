(function() {
    angular.module('theHiveDirectives')
        .directive('multiRowAreaFormatterString', function() {
          return {
            require: 'ngModel',
            link: function(scope, element, attrs, controller) {
                controller.$formatters.push(function(value) {
                    return value ? value.join('\n') : null;
                });
                controller.$parsers.push(function(value) {
                    return value ? value.split('\n') : [null];
                });
            }
          };
        });
})();