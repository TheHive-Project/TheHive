(function () {
    'use strict';
    angular.module('theHiveDirectives')
        .directive('caseReport', function ($compile) {
            return {
                restrict: 'AEC',
                replace: false,
                scope: {
					abstract: '=',
					caze: '=',
                    templates: '=',
                    artifacts: '=',
                    tasks: '='
                },
                transclude : true,
                link: function (scope, element, attributes) {
		            var defaultTemplate = null;
	                for (var ii = 0; ii < scope.templates.length; ++ii) {
						if (scope.templates[ii].isDefault === true) {
							defaultTemplate = scope.templates[ii];
						}
	                }
	                
	                element.html(defaultTemplate.content).show();
	                var e = $compile(element.contents())(scope);
	                element.replaceWith(e);
	                scope.$root.apply();
                }
            };
        });
})();
